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
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.loginReq
import com.mikai233.shared.message.*
import io.netty.channel.ChannelHandlerContext

class ChannelActor(
    context: ActorContext<ChannelMessage>,
    private val handlerContext: ChannelHandlerContext,
    private val timers: TimerScheduler<ChannelMessage>,
    private val buffer: StashBuffer<ChannelMessage>,
    private val gateNode: GateNode,
) : AbstractBehavior<ChannelMessage>(context) {
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
    private val playerActor = gateNode.playerActor()
    private val worldActor = gateNode.worldActor()
    private val protobufPrinter = protobufJsonPrinter()

    init {
        logger.info("{} preStart", context.self)
        playerActor.tell(
            shardingEnvelope(
                112233.toString(),
                PlayerProtobufEnvelope(loginReq { })
            )
        )
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
            playerActor.tell(shardingEnvelope("$playerId", message))
        } else {
            logger.warn("try to message to uninitialized playerId")
        }
    }

    private fun tellWorld(worldId: Long, message: SerdeWorldMessage) {
        if (worldId > 0L) {
            worldActor.tell(shardingEnvelope("$worldId", message))
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
            tellWorld(worldId, WorldProtobufEnvelope(inner))
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