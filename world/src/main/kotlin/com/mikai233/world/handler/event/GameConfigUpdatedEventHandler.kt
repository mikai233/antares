package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdatedEvent
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class GameConfigUpdatedEventHandler : MessageHandler<GameConfigUpdatedEvent> {
    override fun handle(context: HandlerContext, message: GameConfigUpdatedEvent) = Unit
}
