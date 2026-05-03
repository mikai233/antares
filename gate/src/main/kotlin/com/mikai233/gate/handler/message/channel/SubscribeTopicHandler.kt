package com.mikai233.gate.handler.message.channel

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.gate.ChannelActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class SubscribeTopicHandler : MessageHandler<SubscribeTopic> {
    override fun handle(context: HandlerContext, message: SubscribeTopic) {
        val actor = context.requireActor<ChannelActor>()
        actor.subscribe(message.topic)
    }
}
