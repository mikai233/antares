package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.config.ActorConfigSyncMem
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import io.github.realmlabs.asteria.config.ConfigService

@AllOpen
class PlayerLoginEventHandler : PlayerMessageHandler<PlayerLoginEvent> {
    override fun handle(context: PlayerHandlerContext, message: PlayerLoginEvent) {
        val actor = context.actor
        val snapshot = actor.node.services.get(ConfigService::class).current()
        val sync = actor.manager.get<ActorConfigSyncMem>()
        if (sync.currentRevision() == snapshot.revision.version) {
            return
        }
        actor.node.configChangeDispatcher.catchUp(actor, snapshot)
        sync.updateRevision(snapshot.revision.version)
    }
}
