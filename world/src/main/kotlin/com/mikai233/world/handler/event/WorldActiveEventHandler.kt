package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.world.WorldActor
import com.mikai233.world.service.worldService
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class WorldActiveEventHandler : MessageHandler<ActorHandlerContext<WorldActor>, WorldActiveEvent> {
    private val logger = logger()

    override fun handle(context: ActorHandlerContext<WorldActor>, message: WorldActiveEvent) {
        val actor = context.actor
        tryCatch(logger) { worldService.onGameConfigUpdated(actor) }
    }
}
