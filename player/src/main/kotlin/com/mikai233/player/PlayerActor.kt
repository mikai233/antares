package com.mikai233.player

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Terminated
import akka.actor.typed.javadsl.*
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.extension.*
import com.mikai233.common.inject.XKoin
import com.mikai233.player.component.PlayerMessageDispatcher
import com.mikai233.player.component.PlayerScriptSupport
import com.mikai233.player.component.PlayerSharding
import com.mikai233.protocol.ProtoLogin.ConnectionExpiredNotify
import com.mikai233.shared.logMessage
import com.mikai233.shared.message.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds

class PlayerActor(
    context: ActorContext<PlayerMessage>,
    private val buffer: StashBuffer<PlayerMessage>,
    private val timers: TimerScheduler<PlayerMessage>,
    val playerId: Long,
    val koin: XKoin,
) : AbstractBehavior<PlayerMessage>(context), KoinComponent by koin {
    companion object {
        val playerTick = 100.milliseconds
    }

    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { ActorNamedRunnable("playerActorCoroutine", it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private var channelActor: ActorRef<SerdeChannelMessage>? = null
    private val dispatcher by inject<PlayerMessageDispatcher>()
    private val protobufDispatcher = dispatcher.protobufDispatcher
    private val internalDispatcher = dispatcher.internalDispatcher
    private val playerSharding by inject<PlayerSharding>()
    private val playerActorSharding = playerSharding.playerActorSharding
    private val worldActorSharding = playerSharding.worldActorSharding
    private val playerScriptSupport by inject<PlayerScriptSupport>()
    private val localScriptActor = playerScriptSupport.localScriptActor
    val manager = PlayerDataManager(this, coroutine)

    init {
        logger.info("{} preStart", playerId)
        context.system.subscribe<PlayerMessage, ExcelUpdate>(context.self)
    }

    override fun createReceive(): Receive<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            logMessage(logger, message) { "playerId:$playerId" }
            when (message) {
                StopPlayer -> {
                    return@onMessage Behaviors.stopped()
                }

                PlayerInitDone -> {
                    timers.startTimerWithFixedDelay(PlayerTick, playerTick)
                    return@onMessage buffer.unstashAll(active())
                }

                is ActorNamedRunnable -> {
                    handlePlayerActorRunnable(message)
                }

                is WHPlayerCreate -> {
                    handleBusinessPlayerMessage(message)
                }

                else -> {
                    startLoadingPlayerData(message)
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun startLoadingPlayerData(message: PlayerMessage) {
        buffer.stash(message)
        runCatching(manager::loadAll).onFailure {
            logger.error("$playerId load data error, stop the player", it)
            stop()
        }
    }

    private fun active(): Behavior<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is PlayerProtobufEnvelope -> {
                    logMessage(logger, message) { "playerId:$playerId" }
                    handlePlayerProtobufEnvelope(message)
                }

                is ActorNamedRunnable -> {
                    handlePlayerActorRunnable(message)
                }

                PlayerInitDone -> unexpectedMessage(message)

                StopPlayer -> {
                    manager.stopAndFlush()
                    return@onMessage stopping()
                }

                is ExecutePlayerScript -> {
                    message.script.invoke(this)
                }

                is PlayerScript -> {
                    compilePlayerScript(message)
                }

                is BusinessPlayerMessage -> {
                    logMessage(logger, message) { "playerId:$playerId" }
                    handleBusinessPlayerMessage(message)
                }

                PlayerTick -> {
                    manager.tick()
                }
            }
            Behaviors.same()
        }.onSignal(Terminated::class.java) { terminated ->
            val who = terminated.ref
            if (who == channelActor) {
                channelActor = null
                logger.info("player:{} channel actor:{} terminated", playerId, who)
            }
            Behaviors.same()
        }.build()
    }

    private fun handlePlayerProtobufEnvelope(message: PlayerProtobufEnvelope) {
        val inner = message.inner
        protobufDispatcher.dispatch(inner::class, this, inner)
    }

    private fun handleBusinessPlayerMessage(message: BusinessPlayerMessage) {
        internalDispatcher.dispatch(message::class, this, message)
    }

    private fun stopping(): Behavior<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is ActorNamedRunnable -> {
                    handlePlayerActorRunnable(message)
                }

                PlayerInitDone -> unexpectedMessage(message)

                is BusinessPlayerMessage -> Unit

                StopPlayer -> {
                    context.system.unsubscribe(context.self)
                    coroutine.cancelAll("StopPlayer_$playerId")
                    return@onMessage Behaviors.stopped()
                }

                is ExecutePlayerScript -> {
                    executePlayerScript(message)
                }

                is PlayerScript -> {
                    compilePlayerScript(message)
                }

                PlayerTick -> {
                    if (manager.stopAndFlush()) {
                        stop()
                    }
                }
            }
            Behaviors.same()
        }.onSignal(PostStop::class.java) { message ->
            logger.info("player:{} {}", playerId, message)
            Behaviors.same()
        }.build()
    }

    fun isOnline() = channelActor != null

    fun write(message: GeneratedMessageV3) {
        val channel = channelActor
        if (channel != null) {
            val envelope = ChannelProtobufEnvelope(message)
            channel tell envelope
        } else {
            logger.warn("player:{} unable to write message to channel actor, because channel actor is null", playerId)
        }
    }

    fun stop() {
        context.self tell StopPlayer
    }

    fun bindChannelActor(incomingChannelActor: ActorRef<SerdeChannelMessage>) {
        if (incomingChannelActor != channelActor) {
            channelActor?.let {
                context.unwatch(it)
                logger.info("player:{} unbind old channel actor:{}", playerId, it)
                it tell ChannelExpired(ConnectionExpiredNotify.Reason.MultiLogin_VALUE)
            }
            channelActor = incomingChannelActor
            context.watch(channelActor)
            logger.info("player:{} bind new channel actor:{}", playerId, channelActor)
        }
    }

    private fun compilePlayerScript(message: PlayerScript) {
        localScriptActor.tell(CompilePlayerActorScript(message.script, context.self))
    }

    private fun executePlayerScript(message: ExecutePlayerScript) {
        message.script.invoke(this)
    }

    fun tellPlayer(playerId: Long, message: SerdePlayerMessage) {
        playerActorSharding.tell(shardingEnvelope("$playerId", message))
    }

    fun tellWorld(worldId: Long, message: SerdeWorldMessage) {
        worldActorSharding.tell(shardingEnvelope("$worldId", message))
    }

    private fun handlePlayerActorRunnable(message: ActorNamedRunnable): Behavior<WorldMessage> {
        runCatching(message::run).onFailure {
            logger.error("player actor handle runnable:{} failed", message.name, it)
        }
        return Behaviors.same()
    }

    fun submit(name: String, block: () -> Unit) {
        context.self tell ActorNamedRunnable(name, block)
    }
}
