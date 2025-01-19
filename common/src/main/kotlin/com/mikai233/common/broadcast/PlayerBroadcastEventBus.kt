package com.mikai233.common.broadcast

import akka.actor.ActorRef
import akka.event.japi.LookupEventBus
import com.mikai233.common.extension.tell

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
