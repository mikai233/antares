package com.mikai233.gm

import akka.actor.Props
import com.mikai233.common.core.actor.StatefulActor

class MonitorActor(node: GmNode) : StatefulActor<GmNode>(node) {
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
