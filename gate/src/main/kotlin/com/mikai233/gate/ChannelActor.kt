package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.broadcast.Topic
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.encodeActorRef
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.tell
import com.mikai233.common.message.formatMessage
import com.mikai233.common.runtime.gameTimeSource
import com.mikai233.common.runtime.playerBroadcastEventBus
import com.mikai233.common.runtime.system
import com.mikai233.common.time.ActorGameTime
import com.mikai233.gate.crypto.AESCipher
import com.mikai233.gate.crypto.ECDH
import com.mikai233.gate.message.ChannelExpired
import com.mikai233.gate.message.ClientProtobuf
import com.mikai233.gate.message.StopChannel
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.ProtoRpcGate.ChannelExpiredReq
import com.mikai233.protocol.ProtoRpcPlayer.PlayerChannelClosedReq
import com.mikai233.protocol.connectionExpiredNotify
import io.github.realmlabs.asteria.actor.AsteriaActor
import io.github.realmlabs.asteria.gateway.GatewaySession
import io.github.realmlabs.asteria.message.dispatchActor
import io.github.realmlabs.asteria.script.pekko.ActorScriptSupport
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.ReceiveTimeout
import org.apache.pekko.cluster.pubsub.DistributedPubSub
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.Subscribe
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.Unsubscribe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ChannelActor(val node: GateNode, private val session: GatewaySession) :
    AsteriaActor<GateNode>(node) {
    companion object {
        val MaxIdleDuration = 1.minutes

        fun props(node: GateNode, session: GatewaySession): Props =
            Props.create(ChannelActor::class.java, node, session)
    }

    var playerId: Long? = null
    private var worldId: Long? = null
    private lateinit var clientPublicKey: ByteArray
    private val mediator = DistributedPubSub.get(context.system).mediator()
    private val scripts = ActorScriptSupport(this)
    private val subscribedTopics = mutableSetOf<String>()
    private var state = ChannelState.Connecting
    val gameTime: ActorGameTime = node.gameTimeSource.actorTime()

    override fun preStart() {
        super.preStart()
        logger.info("{} started", remoteActorRefAddress())
        context.setReceiveTimeout(MaxIdleDuration.toJavaDuration())
        subscribe(Topic.All_WORLDS_TOPIC)
        mediator.tell(Subscribe(Topic.WORLD_ACTIVE, self))
    }

    override fun postStop() {
        super.postStop()
        notifyPlayerChannelClosed()
        unsubscribeAll()
        mediator.tell(Unsubscribe(Topic.WORLD_ACTIVE, self))
        logger.info("{} stopped", remoteActorRefAddress())
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) { handleClientMessage(it) }
            .match(LocalClientProtobuf::class.java) { handleLocalClientMessage(it) }
            .match(GeneratedMessage::class.java) { handleServerMessage(it) }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ChannelExpired::class.java) { expire(it.reason) }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
            .let(::withScripts)
    }

    fun write(message: GeneratedMessage) {
        session.write(node.protocolCodec.encodeServer(message))
    }

    private fun handleClientMessage(clientProtobuf: ClientProtobuf) {
        when (state) {
            ChannelState.Connecting -> handleClientConnectMessage(clientProtobuf)
            ChannelState.Authenticating -> handleAuthenticatingClientMessage(clientProtobuf)
            ChannelState.Authorized -> forwardClientMessage(clientProtobuf)
        }
    }

    private fun handleClientConnectMessage(clientProtobuf: ClientProtobuf) {
        val message = clientProtobuf.message
        if (message !is LoginReq) {
            logger.warning(
                "{} receive unexpected client side message:{} when not authorized",
                self,
                formatMessage(message),
            )
            stopChannel()
            return
        }
        clientPublicKey = message.clientPublicKey.toByteArray()
        worldId = message.worldId
        state = ChannelState.Authenticating
        node.worldSharding.tell(message, self)
    }

    private fun handleAuthenticatingClientMessage(clientProtobuf: ClientProtobuf) {
        logger.warning(
            "{} unexpected client message:{} while authenticating, stop the channel",
            self,
            formatMessage(clientProtobuf.message),
        )
        stopChannel()
    }

    private fun handleServerMessage(message: GeneratedMessage) {
        when (state) {
            ChannelState.Connecting -> {
                logger.warning(
                    "{} receive unexpected server side message:{} when not authorized",
                    self,
                    formatMessage(message),
                )
            }

            ChannelState.Authenticating -> handleAuthenticatingServerMessage(message)
            ChannelState.Authorized -> handleAuthorizedGeneratedMessage(message)
        }
    }

    private fun handleAuthenticatingServerMessage(message: GeneratedMessage) {
        when (message) {
            is LoginResp -> handleLoginResp(message)
            is ChannelExpiredReq -> dispatchProtobufMessage(message)
            else -> stash()
        }
    }

    private fun handleLoginResp(resp: LoginResp) {
        when (resp.result) {
            ProtoLogin.LoginResult.Success -> {
                handleLoginSuccess(resp)
            }

            ProtoLogin.LoginResult.RegisterLimit,
            ProtoLogin.LoginResult.WorldNotExists,
            ProtoLogin.LoginResult.WorldClosed,
            ProtoLogin.LoginResult.AccountBan,
                -> {
                handleLoginFailed(resp)
            }

            ProtoLogin.LoginResult.UNRECOGNIZED, null -> {
                logger.error("unknown login result, stop the channel")
                handleLoginFailed(resp)
            }
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
            authorizeChannel()
        }
    }

    private fun handleLoginFailed(resp: LoginResp) {
        write(resp)
        stopChannel()
    }

    private fun authorizeChannel() {
        subscribe(Topic.ofWorld(requireNotNull(worldId) { "worldId is null" }))
        subscribe(Topic.CROSS_WORLD_CHAT)
        state = ChannelState.Authorized
        unstashAll()
    }

    private fun withScripts(receive: Receive): Receive {
        return receive.orElse(scripts.receive())
    }

    fun expire(reason: Int) {
        write(connectionExpiredNotify { reasonValue = reason })
        stopChannel()
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
            node.gatewayRouter.dispatch(session, clientProtobuf)
        } catch (e: Exception) {
            logger.error(e, "channel:{} forward client message:{} failed", self, clientProtobuf.message)
        }
    }

    private fun handleLocalClientMessage(message: LocalClientProtobuf) {
        if (state != ChannelState.Authorized) {
            logger.warning(
                "{} receive unexpected local client message:{} when not authorized",
                self,
                formatMessage(message.message),
            )
            stopChannel()
            return
        }
        dispatchProtobufMessage(message.message)
    }

    private fun dispatchProtobufMessage(message: GeneratedMessage) {
        try {
            node.protobufDispatcher.dispatchActor(node, this, message)
        } catch (e: Exception) {
            logger.error(e, "channel:{} handle protobuf message:{} failed", self, message)
        }
    }

    private fun handleAuthorizedGeneratedMessage(message: GeneratedMessage) {
        if (node.protobufDispatcher.canDispatch(message::class)) {
            dispatchProtobufMessage(message)
            return
        }
        invokeOnTargetMode(node.runtimeEnv.serverMode, ServerMode.DevMode) {
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

    private fun notifyPlayerChannelClosed() {
        val currentPlayerId = playerId ?: return
        val drainContext = node.connectionDrainer.drainContext
        val requestBuilder = PlayerChannelClosedReq.newBuilder()
            .setPlayerId(currentPlayerId)
        if (drainContext != null) {
            requestBuilder
                .setShutdown(true)
                .setShutdownPlanId(drainContext.planId)
                .setCoordinatorActor(drainContext.coordinator.encodeActorRef(node.system))
        }
        node.playerSharding.tell(
            requestBuilder.build(),
            self,
        )
    }

    private enum class ChannelState {
        Connecting,
        Authenticating,
        Authorized,
    }
}
