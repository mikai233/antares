package com.mikai233.gate

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.*
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
import com.mikai233.protocol.pingResp
import com.mikai233.shared.codec.CryptoCodec
import com.mikai233.shared.logMessage
import com.mikai233.shared.message.*
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ChannelActor(
    context: ActorContext<ChannelMessage>,
    private val handlerContext: ChannelHandlerContext,
    private val timers: TimerScheduler<ChannelMessage>,
    private val buffer: StashBuffer<ChannelMessage>,
    private val koin: XKoin
) : AbstractBehavior<ChannelMessage>(context), KoinComponent by koin {
    enum class State {
        Connected,
        WaitForAuth,
        Authorized,
    }

    private var state = State.Connected
    private val runnableAdapter = runnableAdapter { ChannelRunnable(it::run) }
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

    init {
        logger.debug("{} preStart", context.self)
    }

    override fun createReceive(): Receive<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ChannelRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    logMessage(logger, message) { "worldId:$worldId" }
                    handleClientConnectMessage(message)
                }

                is StopChannel -> {
                    logger.info("{} {}", context.self, message)
                    Behaviors.stopped()
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
                    logger.debug("{} {}", context.self, message)
                    Behaviors.stopped()
                }

                ChannelAuthorized -> unexpectedMessage(message)
            }
        }.onSignal(PostStop::class.java) {
            onChannelStopped()
            Behaviors.same()
        }.build()
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
            Behaviors.stopped()
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
        return Behaviors.stopped()
    }

    private fun waitForAuthResult(): Behavior<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ChannelRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    logger.warn(
                        "{} unexpected protobuf message:{} while waiting for auth, stop the channel",
                        context.self,
                        protobufPrinter.print(message.inner),
                    )
                    Behaviors.stopped()
                }

                is ChannelExpired -> {
                    handleChannelExpired()
                }

                is ChannelProtobufEnvelope -> {
                    logMessage(logger, message) { "playerId:$playerId worldId:$worldId" }
                    handleWaitForAuthResultMessage(message)
                }

                is StopChannel -> {
                    Behaviors.stopped()
                }

                ChannelAuthorized -> {
                    buffer.unstashAll(authorized())
                }
            }
        }.build()
    }

    private fun handleChannelExpired(): Behavior<ChannelMessage>? {
        logger.debug("{} channel expired while waiting for auth, stop the channel", context.self)
        return Behaviors.stopped()
    }

    private fun authorized(): Behavior<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ChannelRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    forwardClientMessage(message)
                    Behaviors.same()
                }

                is ChannelExpired -> {
                    handleChannelExpired()
                }

                is ChannelProtobufEnvelope -> {
                    logMessage(logger, message) { "playerId:$playerId worldId:$worldId" }
                    write(message.inner)
                    Behaviors.same()
                }

                is StopChannel -> {
                    Behaviors.stopped()
                }

                ChannelAuthorized -> unexpectedMessage(message)
            }
        }.build()
    }

    private fun handleChannelRunnable(message: ChannelRunnable): Behavior<ChannelMessage>? {
        message.run()
        return Behaviors.same()
    }

    private fun onChannelStopped() {
        handlerContext.close()
    }

    private fun stopSelf(reason: StopReason) {
        context.self.tell(StopChannel(reason))
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
}
