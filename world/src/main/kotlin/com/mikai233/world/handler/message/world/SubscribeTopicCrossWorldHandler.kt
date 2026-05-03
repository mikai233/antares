package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.SubscribeTopicReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
class SubscribeTopicCrossWorldHandler : WorldMessageHandler<CrossWorldSubscribeTopicReq> {
    override fun handle(context: WorldHandlerContext, message: CrossWorldSubscribeTopicReq) {
        val actor = context.actor
        actor.sessionManager.send(
            message.playerId,
            SubscribeTopicReq.newBuilder()
                .setTopic(message.topic)
                .build(),
        )
    }
}
