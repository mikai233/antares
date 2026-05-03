package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import com.mikai233.world.service.worldService

@AllOpen
class WorldActiveEventHandler : WorldMessageHandler<WorldActiveEvent> {
    private val logger = logger()

    override fun handle(context: WorldHandlerContext, message: WorldActiveEvent) {
        val actor = context.actor
        tryCatch(logger) { worldService.onGameConfigUpdated(actor) }
    }
}
