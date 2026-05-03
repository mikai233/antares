package com.mikai233.gate.handler.message.broadcast

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.gate.ChannelHandlerContext
import com.mikai233.gate.ChannelMessageHandler
import com.mikai233.protocol.ProtoRpcBroadcast.BroadcastEnvelope
import com.mikai233.protocol.parserForServerMessage

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
class PlayerBroadcastEnvelopeHandler : ChannelMessageHandler<BroadcastEnvelope> {
    override fun handle(context: ChannelHandlerContext, message: BroadcastEnvelope) {
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
