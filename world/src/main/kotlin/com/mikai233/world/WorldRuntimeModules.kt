package com.mikai233.world

import io.github.mikai233.asteria.core.AsteriaModule
import io.github.mikai233.asteria.core.ModuleContext
import org.apache.pekko.actor.ActorSystem

class WorldWakerModule(
    private val node: WorldNode,
) : AsteriaModule {
    override val name: String = "world-waker"

    override suspend fun start(context: ModuleContext) {
        context.services.get(ActorSystem::class).actorOf(WorldWaker.props(node), "worldWaker")
    }
}
