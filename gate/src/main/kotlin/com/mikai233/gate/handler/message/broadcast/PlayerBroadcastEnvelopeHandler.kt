package com.mikai233.gate.handler.message.broadcast

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.message.requireActor
import com.mikai233.gate.ChannelActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class PlayerBroadcastEnvelopeHandler : MessageHandler<PlayerBroadcastEnvelope> {
    override fun handle(context: HandlerContext, message: PlayerBroadcastEnvelope) {
        val actor = context.requireActor<ChannelActor>()
        if (message.include.isNotEmpty() && !message.include.contains(actor.playerId)) {
            return
        }
        if (message.exclude.contains(actor.playerId)) {
            return
        }
        actor.write(message.message)
    }
}
