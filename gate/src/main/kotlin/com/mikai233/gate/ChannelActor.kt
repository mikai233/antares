package com.mikai233.gate

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.*
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.protobufJsonPrinter
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.common.ext.shardingEnvelope
import com.mikai233.common.inject.XKoin
import com.mikai233.gate.component.GateSharding
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.loginReq
import com.mikai233.shared.message.*
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

    init {
        logger.info("{} preStart", context.self)
        context.self.tell(ClientMessage(loginReq {
            account = "mikai233"
            worldId = 1000
        }))
    }

    override fun createReceive(): Receive<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ChannelRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
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
                    logger.info("{} {}", context.self, message)
                    Behaviors.stopped()
                }
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
            logger.warn("try to message to uninitialized playerId")
        }
    }

    private fun tellWorld(worldId: Long, message: SerdeWorldMessage) {
        if (worldId > 0L) {
            worldActorSharding.tell(shardingEnvelope("$worldId", message))
        } else {
            logger.warn("try to message to uninitialized worldId")
        }
    }

    private fun write(message: GeneratedMessageV3) {
        handlerContext.writeAndFlush(message)
    }

    private fun handleClientConnectMessage(message: ClientMessage): Behavior<ChannelMessage> {
        val inner = message.inner
        return if (inner is LoginReq && state == State.Connected) {
            state = State.WaitForAuth
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
                ProtoLogin.LoginResult.Success -> handleLoginSuccess(inner)
                ProtoLogin.LoginResult.RegisterLimit,
                ProtoLogin.LoginResult.WorldNotExists,
                ProtoLogin.LoginResult.WorldClosed,
                ProtoLogin.LoginResult.AccountBan -> {
                    Behaviors.stopped()
                }

                ProtoLogin.LoginResult.UNRECOGNIZED, null -> {
                    logger.error("unexpected login result, stop the channel")
                    Behaviors.stopped()
                }
            }
        } else {
            logger.error("unexpected ChannelProtobufEnvelope:{}, stop the channel", protobufPrinter.print(inner))
            Behaviors.stopped()
        }
    }

    private fun handleLoginSuccess(resp: LoginResp): Behavior<ChannelMessage> {
        state = State.Authorized
        val playerData = resp.playerData
        playerId = playerData.playerId
        write(resp)
        return authorized()
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
                    handleWaitForAuthResultMessage(message)
                }

                is StopChannel -> {
                    Behaviors.stopped()
                }
            }
        }.build()
    }

    private fun handleChannelExpired(): Behavior<ChannelMessage>? {
        logger.info("{} channel expired while waiting for auth, stop the channel", context.self)
        return Behaviors.stopped()
    }

    private fun authorized(): Behavior<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ChannelRunnable -> {
                    handleChannelRunnable(message)
                }

                is ClientMessage -> {
                    tellPlayer(playerId, PlayerProtobufEnvelope(message.inner))
                    Behaviors.same()
                }

                is ChannelExpired -> {
                    handleChannelExpired()
                }

                is ChannelProtobufEnvelope -> {
                    write(message.inner)
                    Behaviors.same()
                }

                is StopChannel -> {
                    Behaviors.stopped()
                }
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

    private fun stop(reason: StopReason) {
        context.self.tell(StopChannel(reason))
    }
}
