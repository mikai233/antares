package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.protocol.ProtoRpc.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import com.mikai233.world.WorldActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class SubscribeTopicCrossWorldHandler : MessageHandler<CrossWorldSubscribeTopicReq> {
    override fun handle(context: HandlerContext, message: CrossWorldSubscribeTopicReq) {
        val actor = context.requireActor<WorldActor>()
        actor.sessionManager.send(
            message.playerId,
            SubscribeTopicReq.newBuilder()
                .setTopic(message.topic)
                .build(),
        )
    }
}
