package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.common.message.world.SubscribeTopicCrossWorld
import com.mikai233.world.WorldActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class SubscribeTopicCrossWorldHandler : MessageHandler<SubscribeTopicCrossWorld> {
    override fun handle(context: HandlerContext, message: SubscribeTopicCrossWorld) {
        val actor = context.requireActor<WorldActor>()
        actor.sessionManager.sendRaw(message.playerId, SubscribeTopic(message.topic))
    }
}
