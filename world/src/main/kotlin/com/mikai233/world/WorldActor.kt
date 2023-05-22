package com.mikai233.world

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.*
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.shared.message.*
import com.mikai233.world.component.WorldActorMessageDispatchers
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class WorldActor(
    context: ActorContext<WorldMessage>,
    private val buffer: StashBuffer<WorldMessage>,
    val timers: TimerScheduler<WorldMessage>,
    val worldId: Long,
    val worldNode: WorldNode
) : AbstractBehavior<WorldMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { WorldRunnable(it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private val protobufDispatcher = worldNode.server.component<WorldActorMessageDispatchers>().protobufDispatcher
    private val internalDispatcher = worldNode.server.component<WorldActorMessageDispatchers>().internalDispatcher

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
                    logger.info("pretend loading world data")
                    coroutine.launch {
                        delay(3.seconds)
                        context.self.tell(WorldInitDone)
                    }
                }

                is WorldRunnable -> {
                    message.run()
                }

                is BusinessWorldMessage -> {
                    buffer.stash(message)
                }

                WorldInitDone -> {
                    return@onMessage buffer.unstashAll(active())
                }
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
                    logger.info("pretend stop player operation")
                    timers.startSingleTimer(StopWorld, 3.seconds.toJavaDuration())
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
                    return@onMessage Behaviors.stopped()
                }

                is WorldRunnable -> {
                    message.run()
                }

                WakeupGameWorld,
                WorldInitDone,
                is WorldProtobufEnvelope -> Unit
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