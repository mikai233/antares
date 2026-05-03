package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.protocol.ProtoRpc.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.UnsubscribeTopicReq
import com.mikai233.world.WorldActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
class UnsubscribeTopicCrossWorldHandler : MessageHandler<CrossWorldUnsubscribeTopicReq> {
    override fun handle(context: HandlerContext, message: CrossWorldUnsubscribeTopicReq) {
        val actor = context.requireActor<WorldActor>()
        actor.sessionManager.send(
            message.playerId,
            UnsubscribeTopicReq.newBuilder()
                .setTopic(message.topic)
                .build(),
        )
    }
}
