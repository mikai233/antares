package com.mikai233.gm

import io.github.mikai233.asteria.actor.AsteriaActor
import org.apache.pekko.actor.Props

class MonitorActor(val node: GmNode) : AsteriaActor<GmNode>(node) {
    companion object {
        fun props(node: GmNode): Props = Props.create(MonitorActor::class.java, node)
    }

    override fun preStart() {
        super.preStart()
        logger.info("{} started", self)
    }

    override fun postStop() {
        super.postStop()
        logger.info("{} stopped", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .build()
    }
}
