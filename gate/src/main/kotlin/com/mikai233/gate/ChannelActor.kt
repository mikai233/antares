package com.mikai233.gate


import com.google.protobuf.GeneratedMessage
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.broadcast.Topic
import com.mikai233.common.core.playerBroadcastEventBus
import com.mikai233.common.core.system
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.crypto.AESCipher
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.tell
import com.mikai233.common.formatMessage
import com.mikai233.common.message.*
import com.mikai233.common.message.dispatchActor
import com.mikai233.protocol.CSEnum
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.ProtoRpc.BroadcastEnvelope
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.UnsubscribeTopicReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.connectionExpiredNotify
import io.github.realmlabs.asteria.gateway.GatewaySession
import io.github.realmlabs.asteria.gateway.GatewaySessionContext
import io.github.realmlabs.asteria.script.pekko.ScriptableAsteriaActor
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.ReceiveTimeout
import org.apache.pekko.cluster.pubsub.DistributedPubSub
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.Subscribe
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.Unsubscribe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ChannelActor(val node: GateNode, private val session: GatewaySession) :
    ScriptableAsteriaActor<GateNode>(node) {
    companion object {
        val MaxIdleDuration = 1.minutes

        fun props(node: GateNode, session: GatewaySession): Props =
            Props.create(ChannelActor::class.java, node, session)
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
            .match(GeneratedMessage::class.java) {
                logger.warning(
                    "{} receive unexpected server side message:{} when not authorized",
                    self,
                    formatMessage(it),
                )
            }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
    }

    fun write(message: GeneratedMessage) {
        session.write(node.protocolCodec.encodeServer(message))
    }

    private fun handleClientConnectMessage(clientProtobuf: ClientProtobuf) {
        val message = clientProtobuf.message
        return if (message is LoginReq) {
            clientPublicKey = message.clientPublicKey.toByteArray()
            worldId = message.worldId
            node.worldSharding.tell(message, self)
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
        session.set(GatePlayerIdKey, playerData.playerId)
        session.set(GateWorldIdKey, requireNotNull(worldId) { "worldId is null" })
        val serverKeyPair = ECDH.genKeyPair()
        val keyResp = resp.toBuilder().setServerPublicKey(serverKeyPair.publicKey.toByteString()).build()
        runCatching {
            write(keyResp)
        }.onFailure {
            logger.error(it, "write server key to client failed, stop the channel")
            stopChannel()
        }.onSuccess {
            val shareKey = ECDH.calculateSharedKey(serverKeyPair.privateKey, clientPublicKey)
            session.enableGateCipher(AESCipher(shareKey))
            self tell ChannelAuthorized
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
            .match(GeneratedMessage::class.java) { tryJudgeAuthResult(it) }
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
            .match(LocalClientProtobuf::class.java) { handleProtobuf(it.message) }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(GeneratedMessage::class.java) { handleAuthorizedGeneratedMessage(it) }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
    }

    /**
     * 断开和客户端的连接
     */
    private fun stopChannel() {
        session.closeGateChannel()
        context.stop(self)
    }

    private fun forwardClientMessage(clientProtobuf: ClientProtobuf) {
        logger.debug("forward message:{}", formatMessage(clientProtobuf.message))
        try {
            runBlocking {
                node.gatewayRouter.dispatch(session, clientProtobuf)
            }
        } catch (e: Exception) {
            logger.error(e, "channel:{} forward client message:{} failed", self, clientProtobuf.message)
        }
    }

    private fun handleProtobuf(message: GeneratedMessage) {
        try {
            node.protobufDispatcher.dispatchActor(node, this, message)
        } catch (e: Exception) {
            logger.error(e, "channel:{} handle protobuf message:{} failed", self, message)
        }
    }

    private fun handleAuthorizedGeneratedMessage(message: GeneratedMessage) {
        when (message) {
            is SubscribeTopicReq, is UnsubscribeTopicReq, is BroadcastEnvelope -> handleProtobuf(message)
            else -> {
                invokeOnTargetMode(ServerMode.DevMode) {
                    logger.info(
                        "{} playerId:{} worldId:{} receive server message:{}",
                        remoteActorRefAddress(),
                        playerId,
                        worldId,
                        formatMessage(message),
                    )
                }
                write(message)
            }
        }
    }

    private fun remoteActorRefAddress(): String {
        val path = self.path().toStringWithAddress(node.system.provider().defaultAddress)
        return "Actor[$path]"
    }

    fun subscribe(topic: String) {
        subscribedTopics.add(topic)
        node.playerBroadcastEventBus.subscribe(self, topic)
    }

    fun unsubscribe(topic: String) {
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
