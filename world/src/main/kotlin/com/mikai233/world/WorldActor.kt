package com.mikai233.world

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.*
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.common.ext.tell
import com.mikai233.common.ext.unixTimestamp
import com.mikai233.common.inject.XKoin
import com.mikai233.shared.message.*
import com.mikai233.world.component.WorldActorDispatchers
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
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { WorldRunnable(it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private val dispatcher: WorldActorDispatchers by inject()
    private val protobufDispatcher = dispatcher.protobufDispatcher
    private val internalDispatcher = dispatcher.internalDispatcher
    private val worldSharding by inject<WorldSharding>()
    val playerActor = worldSharding.playerActor
    val worldActor = worldSharding.worldActor
    val sessionManager = WorldSessionManager(this)
    val manager = WorldDataManager(this, coroutine)

    init {
        logger.info("worldId:{} preStart", worldId)
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
                    logger.info("loading world data")
                    manager.loadAll()
                }

                is WorldRunnable -> {
                    message.run()
                }

                is BusinessWorldMessage -> {
                    buffer.stash(message)
                }

                WorldInitDone -> {
                    timers.startTimerAtFixedRate(WorldTick, 100.milliseconds.toJavaDuration())
                    //FIXME test
                    manager.worldActionMem.worldAction.actionTime = unixTimestamp()
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
                    logger.info("pretend stop player operation")
                    return@onMessage stopping()
                }

                is WorldRunnable -> {
                    message.run()
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
                    coroutine.cancelAll("StopWorld")
                    return@onMessage Behaviors.stopped()
                }

                is WorldRunnable -> {
                    message.run()
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
        val inner = message.inner
        protobufDispatcher.dispatch(inner::class, this, inner)
    }

    fun stop() {
        context.self.tell(StopWorld)
    }

    private fun executeWorldScript(message: ExecuteWorldScript) {
        message.script.invoke(this)
    }
}
