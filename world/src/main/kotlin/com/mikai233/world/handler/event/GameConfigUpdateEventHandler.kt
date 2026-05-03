package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import com.mikai233.world.service.worldService

@AllOpen
class GameConfigUpdateEventHandler : WorldMessageHandler<GameConfigUpdateEvent> {
    private val logger = logger()

    override fun handle(context: WorldHandlerContext, message: GameConfigUpdateEvent) {
        val actor = context.actor
        tryCatch(logger) { worldService.onGameConfigUpdated(actor) }
    }
}
