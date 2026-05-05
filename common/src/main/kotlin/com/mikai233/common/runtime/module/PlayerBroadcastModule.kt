package com.mikai233.common.runtime.module

import com.mikai233.common.broadcast.PlayerBroadcastActor
import com.mikai233.common.broadcast.PlayerBroadcastEventBus
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.routing.FromConfig

class PlayerBroadcastRuntime(
    val eventBus: PlayerBroadcastEventBus,
    val router: ActorRef,
)

class PlayerBroadcastModule : AsteriaModule {
    override val name: String = "player-broadcast"

    override suspend fun install(context: ModuleContext) {
        context.services.register(PlayerBroadcastEventBus::class, PlayerBroadcastEventBus())
    }

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        val eventBus = context.services.get(PlayerBroadcastEventBus::class)
        system.actorOf(PlayerBroadcastActor.props(eventBus), PlayerBroadcastActor.NAME)
        val router = system.actorOf(FromConfig.getInstance().props(), "broadcastRouter")
        context.services.register(PlayerBroadcastRuntime::class, PlayerBroadcastRuntime(eventBus, router))
    }
}
