package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler

@AllOpen
class GameConfigUpdatedEventHandler : PlayerMessageHandler<GameConfigUpdatedEvent> {
    override fun handle(context: PlayerHandlerContext, message: GameConfigUpdatedEvent) = Unit
}
