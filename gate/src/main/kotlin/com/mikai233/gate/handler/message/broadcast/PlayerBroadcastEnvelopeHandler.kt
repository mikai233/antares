package com.mikai233.gate.handler.message.broadcast

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoRpc.BroadcastEnvelope
import com.mikai233.protocol.parserForServerMessage
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerBroadcastEnvelopeHandler : MessageHandler<ActorHandlerContext<ChannelActor>, BroadcastEnvelope> {
    override fun handle(context: ActorHandlerContext<ChannelActor>, message: BroadcastEnvelope) {
        val actor = context.actor
        val playerId = actor.playerId
        if (message.includeCount > 0 && playerId !in message.includeList) {
            return
        }
        if (playerId != null && message.excludeList.contains(playerId)) {
            return
        }
        actor.write(parserForServerMessage(message.messageId).parseFrom(message.payload))
    }
}
