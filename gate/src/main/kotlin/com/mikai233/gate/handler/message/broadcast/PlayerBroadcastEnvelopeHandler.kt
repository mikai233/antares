package com.mikai233.gate.handler.message.broadcast

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoRpc.BroadcastEnvelope
import com.mikai233.protocol.parserForServerMessage
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerBroadcastEnvelopeHandler : MessageHandler<BroadcastEnvelope> {
    override fun handle(context: HandlerContext, message: BroadcastEnvelope) {
        val actor = context.requireActor<ChannelActor>()
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
