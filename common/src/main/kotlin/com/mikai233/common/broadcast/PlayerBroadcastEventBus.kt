package com.mikai233.common.broadcast

import com.mikai233.common.extension.tell
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.event.japi.LookupEventBus

class PlayerBroadcastEventBus : LookupEventBus<PlayerBroadcastEnvelope, ActorRef, String>() {
    override fun mapSize(): Int {
        return 8192
    }

    override fun publish(event: PlayerBroadcastEnvelope, subscriber: ActorRef) {
        subscriber.tell(event)
    }

    override fun classify(event: PlayerBroadcastEnvelope): String {
        return event.topic
    }

    override fun compareSubscribers(a: ActorRef, b: ActorRef): Int {
        return a.compareTo(b)
    }
}
