package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.INTERNAL)
class PlayerCreateEventHandler : PlayerMessageHandler<PlayerCreateEvent> {
    override fun handle(context: PlayerHandlerContext, message: PlayerCreateEvent) {
        val actor = context.actor
        actor.self tell PlayerLoginEvent
    }
}
