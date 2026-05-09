package com.mikai233.global.actor

import com.mikai233.common.message.Message
import com.mikai233.global.GlobalNode
import com.mikai233.global.message.HandoffWorker
import io.github.realmlabs.asteria.actor.AsteriaActor
import io.github.realmlabs.asteria.script.pekko.ActorScriptSupport
import org.apache.pekko.actor.Props

class WorkerActor(val node: GlobalNode) : AsteriaActor<GlobalNode>(node) {
    companion object {
        fun props(node: GlobalNode): Props = Props.create(WorkerActor::class.java, node)
    }

    private val scripts = ActorScriptSupport(this)

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
            .match(HandoffWorker::class.java) { context.stop(self) }
            .match(Message::class.java) { handleMessage(it) }
            .build()
            .orElse(scripts.receive())
    }

    private fun handleMessage(message: Message) {
        logger.warning("WorkerActor received unsupported message: {}", message)
    }
}
