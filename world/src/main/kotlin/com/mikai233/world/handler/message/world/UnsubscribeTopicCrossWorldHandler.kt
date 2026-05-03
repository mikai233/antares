package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.common.message.channel.UnsubscribeTopic
import com.mikai233.common.message.world.UnsubscribeTopicCrossWorld
import com.mikai233.world.WorldActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class UnsubscribeTopicCrossWorldHandler : MessageHandler<UnsubscribeTopicCrossWorld> {
    override fun handle(context: HandlerContext, message: UnsubscribeTopicCrossWorld) {
        val actor = context.requireActor<WorldActor>()
        actor.sessionManager.sendRaw(message.playerId, UnsubscribeTopic(message.topic))
    }
}
