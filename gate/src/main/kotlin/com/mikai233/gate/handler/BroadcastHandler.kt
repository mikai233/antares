package com.mikai233.gate.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.common.message.channel.UnsubscribeTopic
import com.mikai233.gate.ChannelActor

@AllOpen
@Suppress("unused")
class BroadcastHandler {
    fun handlePlayerBroadcastEnvelope(actor: ChannelActor, msg: PlayerBroadcastEnvelope) {
        if (msg.include.isNotEmpty() && !msg.include.contains(actor.playerId)) {
            return
        }
        if (msg.exclude.contains(actor.playerId)) {
            return
        }
        actor.write(msg.message)
    }

    fun handleSubscribeTopic(actor: ChannelActor, msg: SubscribeTopic) {
        actor.subscribe(msg.topic)
    }

    fun handleUnsubscribeTopic(actor: ChannelActor, msg: UnsubscribeTopic) {
        actor.unsubscribe(msg.topic)
    }
}
