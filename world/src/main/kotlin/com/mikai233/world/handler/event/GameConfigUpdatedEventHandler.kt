package com.mikai233.world.handler.event

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class GameConfigUpdatedEventHandler : MessageHandler<ActorHandlerContext<WorldActor>, GameConfigUpdatedEvent> {
    override fun handle(context: ActorHandlerContext<WorldActor>, message: GameConfigUpdatedEvent) = Unit
}
