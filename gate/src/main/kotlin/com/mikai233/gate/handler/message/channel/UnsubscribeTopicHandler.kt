package com.mikai233.gate.handler.message.channel

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.gate.ChannelHandlerContext
import com.mikai233.gate.ChannelMessageHandler
import com.mikai233.protocol.ProtoRpcWorld.UnsubscribeTopicReq

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
class UnsubscribeTopicHandler : ChannelMessageHandler<UnsubscribeTopicReq> {
    override fun handle(context: ChannelHandlerContext, message: UnsubscribeTopicReq) {
        val actor = context.actor
        actor.unsubscribe(message.topic)
    }
}
