package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.common.message.requireActor
import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerCreateEventHandler : MessageHandler<HandlerContext, PlayerCreateEvent> {
    override fun handle(context: HandlerContext, message: PlayerCreateEvent) {
        val actor = context.requireActor<PlayerActor>()
        actor.self tell PlayerLoginEvent
    }
}
