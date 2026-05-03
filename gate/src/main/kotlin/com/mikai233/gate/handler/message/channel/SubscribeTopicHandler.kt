package com.mikai233.gate.handler.message.channel

import com.mikai233.common.annotation.AllOpen
import com.mikai233.gate.ChannelHandlerContext
import com.mikai233.gate.ChannelMessageHandler
import com.mikai233.protocol.ProtoRpcWorld.SubscribeTopicReq

@AllOpen
class SubscribeTopicHandler : ChannelMessageHandler<SubscribeTopicReq> {
    override fun handle(context: ChannelHandlerContext, message: SubscribeTopicReq) {
        val actor = context.actor
        actor.subscribe(message.topic)
    }
}
