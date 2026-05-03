package com.mikai233.gate.handler.message.channel

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoRpc.UnsubscribeTopicReq
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class UnsubscribeTopicHandler : MessageHandler<UnsubscribeTopicReq> {
    override fun handle(context: HandlerContext, message: UnsubscribeTopicReq) {
        val actor = context.requireActor<ChannelActor>()
        actor.unsubscribe(message.topic)
    }
}
