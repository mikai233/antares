package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.message.requireActor
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.playerService
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
class GameConfigUpdateEventHandler : MessageHandler<GameConfigUpdateEvent> {
    override fun handle(context: HandlerContext, message: GameConfigUpdateEvent) {
        val actor = context.requireActor<PlayerActor>()
        playerService.onGameConfigUpdated(actor)
    }
}
