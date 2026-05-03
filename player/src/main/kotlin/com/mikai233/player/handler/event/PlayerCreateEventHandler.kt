package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.INTERNAL)
class PlayerCreateEventHandler : PlayerMessageHandler<PlayerCreateEvent> {
    override fun handle(context: PlayerHandlerContext, message: PlayerCreateEvent) {
        val actor = context.actor
        actor.self tell PlayerLoginEvent
    }
}
