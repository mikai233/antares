package com.mikai233.gate.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.Handle
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.message.MessageHandler
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.common.message.channel.UnsubscribeTopic
import com.mikai233.gate.ChannelActor

@AllOpen
@Suppress("unused")
class BroadcastHandler : MessageHandler {
    @Handle
    fun handlePlayerBroadcastEnvelope(actor: ChannelActor, msg: PlayerBroadcastEnvelope) {
        if (msg.include.isNotEmpty() && !msg.include.contains(actor.playerId)) {
            return
        }
        if (msg.exclude.contains(actor.playerId)) {
            return
        }
        actor.write(msg.message)
    }

    @Handle
    fun handleSubscribeTopic(actor: ChannelActor, msg: SubscribeTopic) {
        actor.subscribe(msg.topic)
    }

    @Handle
    fun handleUnsubscribeTopic(actor: ChannelActor, msg: UnsubscribeTopic) {
        actor.unsubscribe(msg.topic)
    }
}
