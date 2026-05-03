package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.playerService
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerLoginEventHandler : MessageHandler<ActorHandlerContext<PlayerActor>, PlayerLoginEvent> {
    private val logger = logger()

    override fun handle(context: ActorHandlerContext<PlayerActor>, message: PlayerLoginEvent) {
        val actor = context.actor
        tryCatch(logger) { playerService.onGameConfigUpdated(actor) }
    }
}
