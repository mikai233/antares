package com.mikai233.gate

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import akka.actor.typed.pubsub.Topic
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.ext.*
import com.mikai233.common.inject.XKoin
import com.mikai233.gate.component.GateSharding
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.connectionExpiredNotify
import com.mikai233.protocol.pingResp
import com.mikai233.shared.codec.CryptoCodec
import com.mikai233.shared.logMessage
import com.mikai233.shared.message.*
import com.mikai233.shared.startAllWorldTopicActor
import com.mikai233.shared.startWorldTopicActor
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ChannelActor(
    context: ActorContext<ChannelMessage>,
    private val handlerContext: ChannelHandlerContext,
    private val timers: TimerScheduler<ChannelMessage>,
    private val buffer: StashBuffer<ChannelMessage>,
    private val koin: XKoin
) : AbstractBehavior<ChannelMessage>(context), KoinComponent by koin {
    companion object {
        val maxIdleDuration = 10.seconds
    }

    enum class State {
        Connected,
        WaitForAuth,
        Authorized,
    }

    private var state = State.Connected
    private val runnableAdapter = runnableAdapter { ActorNamedRunnable("channelActorCoroutine", it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()
    private val logger = actorLogger()
    private var playerId = 0L
    private var birthWorldId = 0L
    private var worldId = 0L
    private val gateSharding by inject<GateSharding>()
    private val playerActorSharding = gateSharding.playerActorSharding
    private val worldActorSharding = gateSharding.worldActorSharding
    private val protobufPrinter = protobufJsonPrinter()
    private lateinit var clientPublicKey: ByteArray
    private lateinit var worldTopicActor: ActorRef<Topic.Command<WorldTopicMessage>>
    private val worldTopicAdapter: ActorRef<WorldTopicMessage> =
        context.messageAdapter(WorldTopicMessage::class.java) { ChannelWorldTopic(it) }
    private lateinit var allWorldTopicActor: ActorRef<Topic.Command<AllWorldTopicMessage>>
    private val allWorldTopicAdapter: ActorRef<AllWorldTopicMessage> =
        context.messageAdapter(AllWorldTopicMessage::class.java) { ChannelAllWorldTopic(it) }

    init {
        logger.debug("{} preStart", context.self)
        context.setReceiveTimeout(maxIdleDuration.toJavaDuration(), ChannelReceiveTimeout)
    }

    override fun createReceive(): Receive<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ActorNamedRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    logMessage(logger, message) { "worldId:$worldId" }
                    handleClientConnectMessage(message)
                }

                is StopChannel -> {
                    handleStopChannel()
                }

                is ChannelProtobufEnvelope -> {
                    logger.warn(
                        "{} unexpected ChannelProtobufEnvelope:{}",
                        context.self,
                        protobufPrinter.print(message.inner)
                    )
                    Behaviors.same()
                }

                is ChannelExpired -> {
                    handleChannelExpired(message)
                }

                ChannelAuthorized,
                is ChannelAllWorldTopic,
                is ChannelWorldTopic -> unexpectedMessage(message)

                ChannelReceiveTimeout -> {
                    stopSelf()
                }
            }
        }.build()
    }

    private fun handleWorldTopic(message: ChannelWorldTopic): Behavior<ChannelMessage> {
        when (val inner = message.inner) {
            is ProtobufEnvelopeToWorldClient -> {
                logMessage(logger, inner.inner) { "playerId:$playerId worldId:$worldId" }
                write(inner.inner)
            }
        }
        return Behaviors.same()
    }

    private fun handleAllWorldTopic(message: ChannelAllWorldTopic): Behavior<ChannelMessage> {
        when (val inner = message.inner) {
            is ProtobufEnvelopeToAllWorldClient -> {
                logMessage(logger, inner.inner) { "playerId:$playerId worldId:$worldId" }
                write(inner.inner)
            }
        }
        return Behaviors.same()
    }

    private fun tellPlayer(playerId: Long, message: SerdePlayerMessage) {
        if (playerId > 0L) {
            playerActorSharding.tell(shardingEnvelope("$playerId", message))
        } else {
            logger.warn("try to send message to uninitialized playerId, this message will be dropped")
        }
    }

    private fun tellWorld(worldId: Long, message: SerdeWorldMessage) {
        if (worldId > 0L) {
            worldActorSharding.tell(shardingEnvelope("$worldId", message))
        } else {
            logger.warn("try to send message to uninitialized worldId, this message will be dropped")
        }
    }

    private fun write(message: GeneratedMessageV3, listener: ChannelFutureListener? = null) {
        val future = handlerContext.writeAndFlush(message)
        listener?.let {
            future.addListener(listener)
        }
    }

    private fun handleClientConnectMessage(message: ClientMessage): Behavior<ChannelMessage> {
        val inner = message.inner
        return if (inner is LoginReq && state == State.Connected) {
            state = State.WaitForAuth
            clientPublicKey = inner.clientPublicKey.toByteArray()
            worldId = inner.worldId
            tellWorld(worldId, PlayerLogin(inner, context.self.narrow()))
            waitForAuthResult()
        } else {
            logger.warn("unexpected protobuf message:{} at state:{}", protobufPrinter.print(inner), state)
            stopSelf()
        }
    }

    private fun handleWaitForAuthResultMessage(message: ChannelProtobufEnvelope): Behavior<ChannelMessage> {
        val inner = message.inner
        return if (inner is LoginResp) {
            when (inner.result) {
                ProtoLogin.LoginResult.Success -> {
                    handleLoginSuccess(inner)
                }

                ProtoLogin.LoginResult.RegisterLimit,
                ProtoLogin.LoginResult.WorldNotExists,
                ProtoLogin.LoginResult.WorldClosed,
                ProtoLogin.LoginResult.AccountBan -> {
                    handleLoginFailed(inner)
                }

                ProtoLogin.LoginResult.UNRECOGNIZED, null -> {
                    logger.error("unexpected login result, stop the channel")
                    handleLoginFailed(inner)
                }
            }
        } else {
            buffer.stash(message)
            Behaviors.same()
        }
    }

    private fun handleLoginSuccess(resp: LoginResp): Behavior<ChannelMessage> {
        state = State.Authorized
        val playerData = resp.data
        playerId = playerData.playerId
        val serverKeyPair = ECDH.genKeyPair()
        val keyResp = resp.toBuilder().setServerPublicKey(serverKeyPair.publicKey.toByteString()).build()
        write(keyResp) {
            if (it.isDone) {
                val shareKey = ECDH.calculateShareKey(serverKeyPair.privateKey, clientPublicKey)
                handlerContext.channel().attr(CryptoCodec.cryptoKey).set(shareKey)
                context.self tell ChannelAuthorized
            }
        }
        return Behaviors.same()
    }

    private fun handleLoginFailed(resp: LoginResp): Behavior<ChannelMessage> {
        write(resp)
        return stopSelf()
    }

    private fun waitForAuthResult(): Behavior<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ActorNamedRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    logger.warn(
                        "{} unexpected protobuf message:{} while waiting for auth, stop the channel",
                        context.self,
                        protobufPrinter.print(message.inner),
                    )
                    stopSelf()
                }

                is ChannelExpired -> {
                    handleChannelExpired(message)
                }

                is ChannelProtobufEnvelope -> {
                    logMessage(logger, message) { "playerId:$playerId worldId:$worldId" }
                    handleWaitForAuthResultMessage(message)
                }

                is StopChannel -> {
                    handleStopChannel()
                }

                ChannelAuthorized -> {
                    onAuthorized()
                }

                is ChannelAllWorldTopic,
                is ChannelWorldTopic -> unexpectedMessage(message)

                ChannelReceiveTimeout -> {
                    stopSelf()
                }
            }
        }.build()
    }

    private fun onAuthorized(): Behavior<ChannelMessage> {
        worldTopicActor = context.startWorldTopicActor(worldId)
        worldTopicActor tell Topic.subscribe(worldTopicAdapter)
        allWorldTopicActor = context.startAllWorldTopicActor()
        allWorldTopicActor tell Topic.subscribe(allWorldTopicAdapter)
        return buffer.unstashAll(authorized())
    }

    private fun handleChannelExpired(message: ChannelExpired): Behavior<ChannelMessage> {
        write(connectionExpiredNotify { reasonValue = message.reason })
        return stopSelf()
    }

    private fun authorized(): Behavior<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ActorNamedRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    forwardClientMessage(message)
                    Behaviors.same()
                }

                is ChannelExpired -> {
                    handleChannelExpired(message)
                }

                is ChannelProtobufEnvelope -> {
                    logMessage(logger, message) { "playerId:$playerId worldId:$worldId" }
                    write(message.inner)
                    Behaviors.same()
                }

                is StopChannel -> {
                    handleStopChannel()
                }

                ChannelAuthorized -> unexpectedMessage(message)
                is ChannelAllWorldTopic -> {
                    handleAllWorldTopic(message)
                }

                is ChannelWorldTopic -> {
                    handleWorldTopic(message)
                }

                ChannelReceiveTimeout -> {
                    stopSelf()
                }
            }
        }.build()
    }

    private fun handleChannelRunnable(message: ActorNamedRunnable): Behavior<ChannelMessage> {
        runCatching(message::run).onFailure {
            logger.error("channel actor handle runnable:{} failed", message.name, it)
        }
        return Behaviors.same()
    }

    private fun stopSelf(): Behavior<ChannelMessage> {
        handlerContext.close()
        return Behaviors.same()
    }

    private fun forwardClientMessage(message: ClientMessage) {
        val inner = message.inner
        if (inner is PingReq) {
            handlePingReq(inner)
        } else {
            logMessage(logger, message) { "playerId:$playerId worldId:$worldId" }
            tellPlayer(playerId, PlayerProtobufEnvelope(inner))
        }
    }

    private fun handlePingReq(req: PingReq) {
        write(pingResp { serverTimestamp = unixTimestamp() })
    }

    private fun handleStopChannel(): Behavior<ChannelMessage> {
        if (this::worldTopicActor.isInitialized) {
            worldTopicActor tell Topic.unsubscribe(worldTopicAdapter)
        }
        if (this::allWorldTopicActor.isInitialized) {
            allWorldTopicActor tell Topic.unsubscribe(allWorldTopicAdapter)
        }
        return Behaviors.stopped {
            logger.debug("player:{} {} channel stopped", playerId, context.self)
        }
    }
}
