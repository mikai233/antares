package com.mikai233.gate


import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.Unsubscribe
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.broadcast.Topic
import com.mikai233.common.codec.CIPHER_KEY
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.crypto.AESCipher
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.tell
import com.mikai233.common.formatMessage
import com.mikai233.common.message.*
import com.mikai233.common.message.world.PlayerLogin
import com.mikai233.protocol.CSEnum
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.connectionExpiredNotify
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ChannelActor(node: GateNode, private val handlerContext: ChannelHandlerContext) : StatefulActor<GateNode>(node) {
    companion object {
        val MaxIdleDuration = 1.minutes

        fun props(node: GateNode, handlerContext: ChannelHandlerContext): Props =
            Props.create(ChannelActor::class.java, node, handlerContext)
    }

    var playerId: Long? = null
    private var worldId: Long? = null
    private lateinit var clientPublicKey: ByteArray
    private val mediator = DistributedPubSub.get(context.system).mediator()
    private val subscribedTopics = mutableSetOf<String>()

    override fun preStart() {
        super.preStart()
        logger.info("{} started", remoteActorRefAddress())
        context.setReceiveTimeout(MaxIdleDuration.toJavaDuration())
        subscribe(Topic.All_WORLDS_TOPIC)
        mediator.tell(Subscribe(Topic.WORLD_ACTIVE, self))
    }

    override fun postStop() {
        super.postStop()
        unsubscribeAll()
        mediator.tell(Unsubscribe(Topic.WORLD_ACTIVE, self))
        logger.info("{} stopped", remoteActorRefAddress())
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) { handleClientConnectMessage(it) }
            .match(ServerProtobuf::class.java) {
                logger.warning(
                    "{} receive unexpected server side message:{} when not authorized",
                    self,
                    formatMessage(it.message),
                )
            }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
    }

    fun write(message: GeneratedMessage, listener: ChannelFutureListener? = null) {
        val future = handlerContext.writeAndFlush(message)
        listener?.let { future.addListener(it) }
    }

    private fun handleClientConnectMessage(clientProtobuf: ClientProtobuf) {
        val message = clientProtobuf.message
        return if (message is LoginReq) {
            clientPublicKey = message.clientPublicKey.toByteArray()
            worldId = message.worldId
            node.worldSharding.tell(PlayerLogin(message.worldId, message), self)
            context.become(authenticating())
        } else {
            logger.warning(
                "{} receive unexpected client side message:{} when not authorized",
                self,
                formatMessage(message),
            )
            stopChannel()
        }
    }

    private fun tryJudgeAuthResult(message: GeneratedMessage) {
        if (message is LoginResp) {
            when (message.result) {
                ProtoLogin.LoginResult.Success -> {
                    handleLoginSuccess(message)
                }

                ProtoLogin.LoginResult.RegisterLimit,
                ProtoLogin.LoginResult.WorldNotExists,
                ProtoLogin.LoginResult.WorldClosed,
                ProtoLogin.LoginResult.AccountBan,
                    -> {
                    handleLoginFailed(message)
                }

                ProtoLogin.LoginResult.UNRECOGNIZED, null -> {
                    logger.error("unknown login result, stop the channel")
                    handleLoginFailed(message)
                }
            }
        } else {
            stash()
        }
    }

    private fun handleLoginSuccess(resp: LoginResp) {
        context.cancelReceiveTimeout()
        val playerData = resp.data
        playerId = playerData.playerId
        val serverKeyPair = ECDH.genKeyPair()
        val keyResp = resp.toBuilder().setServerPublicKey(serverKeyPair.publicKey.toByteString()).build()
        write(keyResp) {
            if (it.isSuccess) {
                val shareKey = ECDH.calculateSharedKey(serverKeyPair.privateKey, clientPublicKey)
                handlerContext.channel().attr(CIPHER_KEY).set(AESCipher(shareKey))
                self tell ChannelAuthorized
            } else {
                logger.error(it.cause(), "write server key to client failed, stop the channel")
                stopChannel()
            }
        }
    }

    private fun handleLoginFailed(resp: LoginResp) {
        write(resp)
        stopChannel()
    }

    private fun authenticating(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) {
                val message = it.message
                logger.warning(
                    "{} unexpected client message:{} while authenticating, stop the channel",
                    self,
                    formatMessage(message),
                )
                stopChannel()
            }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ServerProtobuf::class.java) { tryJudgeAuthResult(it.message) }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ChannelAuthorized::class.java) {
                subscribe(Topic.ofWorld(requireNotNull(worldId) { "worldId is null" }))
                unstashAll()
                context.become(authorized())
            }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
    }

    private fun handleChannelExpired(message: ChannelExpired) {
        write(connectionExpiredNotify { reasonValue = message.reason })
        stopChannel()
    }

    private fun authorized(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) { forwardClientMessage(it) }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ServerProtobuf::class.java) {
                invokeOnTargetMode(ServerMode.DevMode) {
                    logger.info(
                        "{} playerId:{} worldId:{} receive server message:{}",
                        remoteActorRefAddress(),
                        playerId,
                        worldId,
                        formatMessage(it.message),
                    )
                }
                write(it.message)
            }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .match(Message::class.java) { handleChannelMessage(it) }
            .build()
    }

    /**
     * 断开和客户端的连接
     */
    private fun stopChannel() {
        if (handlerContext.channel().isActive) {
            handlerContext.close()
        } else {
            context.stop(self)
        }
    }

    private fun forwardClientMessage(clientProtobuf: ClientProtobuf) {
        val (id, message) = clientProtobuf
        val actor = if (id == CSEnum.GmReq.id) {
            val req = message as GmReq
            MessageForward.whichCommand(req.cmd)
        } else {
            MessageForward.whichActor(id)
        }
        logger.debug("forward message:{} to target:{}", formatMessage(message), actor)
        if (actor == null) {
            logger.warning("proto: {} has no target to forward", clientProtobuf.id)
        } else {
            val playerId = requireNotNull(playerId) { "playerId is null" }
            val worldId = requireNotNull(worldId) { "worldId is null" }
            when (actor) {
                Forward.PlayerActor -> {
                    node.playerSharding.tell(PlayerProtobufEnvelope(playerId, message), self)
                }

                Forward.WorldActor -> {
                    node.worldSharding.tell(WorldProtobufEnvelope(playerId, worldId, message), self)
                }

                Forward.ChannelActor -> {
                    handleProtobuf(message)
                }
            }
        }
    }

    private fun handleProtobuf(message: GeneratedMessage) {
        try {
            node.protobufDispatcher.dispatch(message::class, message, this)
        } catch (e: Exception) {
            logger.error(e, "channel:{} handle protobuf message:{} failed", self, message)
        }
    }

    private fun handleChannelMessage(message: Message) {
        try {
            node.internalDispatcher.dispatch(message::class, message, this)
        } catch (e: Exception) {
            logger.error(e, "channel:{} handle message:{} failed", self, message)
        }
    }

    private fun remoteActorRefAddress(): String {
        val path = self.path().toStringWithAddress(node.system.provider().defaultAddress)
        return "Actor[$path]"
    }

    private fun subscribe(topic: String) {
        subscribedTopics.add(topic)
        node.playerBroadcastEventBus.subscribe(self, topic)
    }

    private fun unsubscribe(topic: String) {
        subscribedTopics.remove(topic)
        node.playerBroadcastEventBus.unsubscribe(self, topic)
    }

    private fun unsubscribeAll() {
        subscribedTopics.forEach { topic ->
            node.playerBroadcastEventBus.unsubscribe(self, topic)
        }
        subscribedTopics.clear()
    }
}
