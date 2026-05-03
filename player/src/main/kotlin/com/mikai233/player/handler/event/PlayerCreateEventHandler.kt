package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerCreateEventHandler : MessageHandler<ActorHandlerContext<PlayerActor>, PlayerCreateEvent> {
    override fun handle(context: ActorHandlerContext<PlayerActor>, message: PlayerCreateEvent) {
        val actor = context.actor
        actor.self tell PlayerLoginEvent
    }
}
