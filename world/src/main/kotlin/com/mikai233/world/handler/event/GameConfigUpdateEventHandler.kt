package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.requireActor
import com.mikai233.world.WorldActor
import com.mikai233.world.service.worldService
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class GameConfigUpdateEventHandler : MessageHandler<GameConfigUpdateEvent> {
    private val logger = logger()

    override fun handle(context: HandlerContext, message: GameConfigUpdateEvent) {
        val actor = context.requireActor<WorldActor>()
        tryCatch(logger) { worldService.onGameConfigUpdated(actor) }
    }
}
