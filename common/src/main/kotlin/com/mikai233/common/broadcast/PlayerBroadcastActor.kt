package com.mikai233.common.broadcast

import akka.actor.AbstractActor
import akka.actor.Props
import com.mikai233.common.core.Node
import com.mikai233.common.extension.actorLogger

class PlayerBroadcastActor(val node: Node) : AbstractActor() {
    companion object {
        const val NAME = "broadcastActor"

        fun props(node: Node): Props = Props.create(PlayerBroadcastActor::class.java, node)
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
            .match(PlayerBroadcastEnvelope::class.java) { node.playerBroadcastEventBus.publish(it) }
            .build()
    }
}
