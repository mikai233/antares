package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.config.ActorConfigSyncMem
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import io.github.realmlabs.asteria.config.ConfigChangedEvent

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.INTERNAL)
class ConfigChangedEventHandler : WorldMessageHandler<ConfigChangedEvent> {
    override fun handle(context: WorldHandlerContext, message: ConfigChangedEvent) {
        val actor = context.actor
        val sync = actor.manager.get<ActorConfigSyncMem>()
        if (sync.currentRevision() == message.currentRevision.version) {
            return
        }
        actor.node.configChangeDispatcher.dispatch(actor, message)
        sync.updateRevision(message.currentRevision.version)
    }
}
