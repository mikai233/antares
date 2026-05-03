package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler

@AllOpen
class GameConfigUpdatedEventHandler : WorldMessageHandler<GameConfigUpdatedEvent> {
    override fun handle(context: WorldHandlerContext, message: GameConfigUpdatedEvent) = Unit
}
