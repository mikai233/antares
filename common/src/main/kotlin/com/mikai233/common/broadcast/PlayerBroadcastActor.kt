package com.mikai233.common.broadcast

import com.mikai233.common.extension.actorLogger
import org.apache.pekko.actor.AbstractActor
import org.apache.pekko.actor.Props

class PlayerBroadcastActor(private val eventBus: PlayerBroadcastEventBus) : AbstractActor() {
    companion object {
        const val NAME = "broadcastActor"

        fun props(eventBus: PlayerBroadcastEventBus): Props = Props.create(PlayerBroadcastActor::class.java, eventBus)
    }

    private val logger = actorLogger()

    override fun preStart() {
        logger.info("{} started", self)
    }

    override fun postStop() {
        logger.info("{} stopped", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(PlayerBroadcastEnvelope::class.java) { eventBus.publish(it) }
            .build()
    }
}
