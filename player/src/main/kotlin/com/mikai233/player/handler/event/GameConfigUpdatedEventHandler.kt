package com.mikai233.player.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class GameConfigUpdatedEventHandler : MessageHandler<ActorHandlerContext<PlayerActor>, GameConfigUpdatedEvent> {
    override fun handle(context: ActorHandlerContext<PlayerActor>, message: GameConfigUpdatedEvent) = Unit
}
