package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.protocol.ProtoRpc.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler

@AllOpen
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
