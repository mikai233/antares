package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.world.WorldActor
import com.mikai233.world.service.worldService

@AllOpen
@Suppress("unused")
class GameConfigHandler : MessageHandler {
    val logger = logger()

    @Handle(GameConfigUpdateEvent::class)
    fun handleGameConfigUpdateEvent(world: WorldActor) {
        tryCatch(logger) { worldService.onGameConfigUpdated(world) }
    }

    @Handle(GameConfigUpdatedEvent::class)
    fun handleGameConfigUpdatedEvent(world: WorldActor) {

    }
}