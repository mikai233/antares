package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.protocol.ProtoRpc.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class SubscribeTopicCrossWorldHandler : MessageHandler<ActorHandlerContext<WorldActor>, CrossWorldSubscribeTopicReq> {
    override fun handle(context: ActorHandlerContext<WorldActor>, message: CrossWorldSubscribeTopicReq) {
        val actor = context.actor
        actor.sessionManager.send(
            message.playerId,
            SubscribeTopicReq.newBuilder()
                .setTopic(message.topic)
                .build(),
        )
    }
}
