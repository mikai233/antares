package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.config.ActorConfigSyncMem
import com.mikai233.common.event.GameConfigChangedEvent
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.INTERNAL)
class ConfigChangedEventHandler : PlayerMessageHandler<GameConfigChangedEvent> {
    override fun handle(context: PlayerHandlerContext, message: GameConfigChangedEvent) {
        val actor = context.actor
        val sync = actor.manager.get<ActorConfigSyncMem>()
        if (sync.currentRevision() == message.currentRevision.version) {
            return
        }
        actor.node.configChangeDispatcher.dispatch(actor, message)
        sync.updateRevision(message.currentRevision.version)
    }
}
