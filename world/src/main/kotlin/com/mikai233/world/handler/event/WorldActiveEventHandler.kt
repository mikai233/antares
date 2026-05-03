package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.config.ActorConfigSyncMem
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import io.github.realmlabs.asteria.config.ConfigService

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.INTERNAL)
class WorldActiveEventHandler : WorldMessageHandler<WorldActiveEvent> {
    override fun handle(context: WorldHandlerContext, message: WorldActiveEvent) {
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
