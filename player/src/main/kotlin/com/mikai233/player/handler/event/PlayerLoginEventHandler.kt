package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.service.playerService

@AllOpen
class PlayerLoginEventHandler : PlayerMessageHandler<PlayerLoginEvent> {
    private val logger = logger()

    override fun handle(context: PlayerHandlerContext, message: PlayerLoginEvent) {
        val actor = context.actor
        tryCatch(logger) { playerService.onGameConfigUpdated(actor) }
    }
}
