package com.mikai233.world

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.javadsl.StashBuffer
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.shared.message.WorldMessage
import com.mikai233.shared.message.WorldRunnable
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
    override fun createReceive(): Receive<WorldMessage> {
        return newReceiveBuilder().build()
    }
}