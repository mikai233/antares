package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.playerService
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class GameConfigUpdateEventHandler : MessageHandler<ActorHandlerContext<PlayerActor>, GameConfigUpdateEvent> {
    override fun handle(context: ActorHandlerContext<PlayerActor>, message: GameConfigUpdateEvent) {
        val actor = context.actor
        playerService.onGameConfigUpdated(actor)
    }
}
