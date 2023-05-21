package com.mikai233.world

import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.*
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.shared.message.*
import com.mikai233.world.component.WorldActorMessageDispatchers

class WorldActor(
    context: ActorContext<WorldMessage>,
    private val buffer: StashBuffer<WorldMessage>,
    val worldId: Long,
    val worldNode: WorldNode
) : AbstractBehavior<WorldMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { WorldRunnable(it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private val protobufDispatcher = worldNode.server.component<WorldActorMessageDispatchers>().protobufDispatcher

    //    private val internalDispatcher = worldNode.server.component<WorldActorMessageDispatchers>().internalDispatcher
    init {
        logger.info("{} preStart", worldId)
    }

    override fun createReceive(): Receive<WorldMessage> {
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> TODO()
                StopWorld -> return@onMessage Behaviors.stopped()
                WakeupGameWorld -> {

                }

                is WorldRunnable -> {
                    message.run()
                }
            }
            Behaviors.same()
        }.onSignal(PostStop::class.java) { message ->
            logger.info("{} {}", worldId, message)
            Behaviors.same()
        }.build()
    }
}