package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.service.playerService

@AllOpen
class GameConfigUpdateEventHandler : PlayerMessageHandler<GameConfigUpdateEvent> {
    override fun handle(context: PlayerHandlerContext, message: GameConfigUpdateEvent) {
        val actor = context.actor
        playerService.onGameConfigUpdated(actor)
    }
}
