package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.UnsubscribeTopicReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
class UnsubscribeTopicCrossWorldHandler : WorldMessageHandler<CrossWorldUnsubscribeTopicReq> {
    override fun handle(context: WorldHandlerContext, message: CrossWorldUnsubscribeTopicReq) {
        val actor = context.actor
        actor.sessionManager.send(
            message.playerId,
            UnsubscribeTopicReq.newBuilder()
                .setTopic(message.topic)
                .build(),
        )
    }
}
