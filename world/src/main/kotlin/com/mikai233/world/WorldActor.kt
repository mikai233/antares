package com.mikai233.world

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.*
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.extension.*
import com.mikai233.common.inject.XKoin
import com.mikai233.shared.message.*
import com.mikai233.shared.startAllWorldTopicActor
import com.mikai233.shared.startWorldTopicActor
import com.mikai233.world.component.WorldMessageDispatcher
import com.mikai233.world.component.WorldSharding
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class WorldActor(
    context: ActorContext<WorldMessage>,
    private val buffer: StashBuffer<WorldMessage>,
    val timers: TimerScheduler<WorldMessage>,
    val worldId: Long,
    val koin: XKoin,
) : AbstractBehavior<WorldMessage>(context), KoinComponent by koin {
    companion object {
        val worldTick = 100.milliseconds
    }

    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { ActorNamedRunnable("worldActorCoroutine", it::run) }
    val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private val dispatcher: WorldMessageDispatcher by inject()
    private val protobufDispatcher = dispatcher.protobufDispatcher
    private val internalDispatcher = dispatcher.internalDispatcher
    private val worldSharding by inject<WorldSharding>()
    private val playerActorSharding = worldSharding.playerActorSharding
    private val worldActorSharding = worldSharding.worldActorSharding
    val sessionManager = WorldSessionManager(this)
    val manager = WorldDataManager(this, coroutine)
    val worldTopic = context.startWorldTopicActor(worldId)
    val allWorldTopic = context.startAllWorldTopicActor()

    init {
        val address = context.system.address()
        logger.info("worldId:{} preStart {}", worldId, context.self)
        context.system.subscribe<WorldMessage, ExcelUpdate>(context.self)
    }

    override fun createReceive(): Receive<WorldMessage> {
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> {
                    executeWorldScript(message)
                }

                StopWorld -> {
                    return@onMessage Behaviors.stopped()
                }

                WakeupGameWorld -> {
                    manager.loadAll()
                }

                is ActorNamedRunnable -> {
                    handleWorldActorRunnable(message)
                }

                is BusinessWorldMessage -> {
                    buffer.stash(message)
                }

                WorldInitDone -> {
                    timers.startTimerAtFixedRate(WorldTick, worldTick.toJavaDuration())
                    return@onMessage buffer.unstashAll(active())
                }

                WorldTick -> Unit
            }
            Behaviors.same()
        }.onSignal(PostStop::class.java) { message ->
            logger.info("worldId:{} {}", worldId, message)
            Behaviors.same()
        }.build()
    }

    private fun active(): Behavior<WorldMessage> {
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> {
                    executeWorldScript(message)
                }

                StopWorld -> {
                    manager.stopAndFlush()
                    return@onMessage stopping()
                }

                is ActorNamedRunnable -> {
                    handleWorldActorRunnable(message)
                }

                WakeupGameWorld,
                WorldInitDone -> Unit

                is WorldProtobufEnvelope -> {
                    handleWorldProtobufEnvelope(message)
                }

                is BusinessWorldMessage -> {
                    handleBusinessWorldMessage(message)
                }

                WorldTick -> {
                    manager.tickDatabase()
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun stopping(): Behavior<WorldMessage> {
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> {
                    executeWorldScript(message)
                }

                StopWorld -> {
                    context.system.unsubscribe(context.self)
                    coroutine.cancelAll("StopWorld_$worldId")
                    return@onMessage Behaviors.stopped()
                }

                is ActorNamedRunnable -> {
                    handleWorldActorRunnable(message)
                }

                WakeupGameWorld,
                WorldInitDone,
                is BusinessWorldMessage -> Unit

                WorldTick -> {
                    if (manager.stopAndFlush()) {
                        context.self tell StopWorld
                    }
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun handleBusinessWorldMessage(message: BusinessWorldMessage) {
        internalDispatcher.dispatch(message::class, this, message)
    }

    private fun handleWorldProtobufEnvelope(message: WorldProtobufEnvelope) {
        val inner = message.message
        protobufDispatcher.dispatch(inner::class, this, inner)
    }

    fun stop() {
        context.self.tell(StopWorld)
    }

    private fun executeWorldScript(message: ExecuteWorldScript) {
        message.script.invoke(this)
    }

    fun tellPlayer(playerId: Long, message: SerdePlayerMessage) {
        playerActorSharding.tell(shardingEnvelope("$playerId", message))
    }

    fun tellWorld(worldId: Long, message: SerdeWorldMessage) {
        worldActorSharding.tell(shardingEnvelope("$worldId", message))
    }

    private fun handleWorldActorRunnable(message: ActorNamedRunnable): Behavior<WorldMessage> {
        runCatching(message::run).onFailure {
            logger.error("world actor handle runnable:{} failed", message.name, it)
        }
        return Behaviors.same()
    }

    fun submit(name: String, block: () -> Unit) {
        context.self tell ActorNamedRunnable(name, block)
    }
}
