package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.protocol.ProtoRpc.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.UnsubscribeTopicReq
import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class UnsubscribeTopicCrossWorldHandler : MessageHandler<ActorHandlerContext<WorldActor>, CrossWorldUnsubscribeTopicReq> {
    override fun handle(context: ActorHandlerContext<WorldActor>, message: CrossWorldUnsubscribeTopicReq) {
        val actor = context.actor
        actor.sessionManager.send(
            message.playerId,
            UnsubscribeTopicReq.newBuilder()
                .setTopic(message.topic)
                .build(),
        )
    }
}
