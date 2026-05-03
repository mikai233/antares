package com.mikai233.gate.handler.message.channel

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class SubscribeTopicHandler : MessageHandler<ActorHandlerContext<ChannelActor>, SubscribeTopicReq> {
    override fun handle(context: ActorHandlerContext<ChannelActor>, message: SubscribeTopicReq) {
        val actor = context.actor
        actor.subscribe(message.topic)
    }
}
