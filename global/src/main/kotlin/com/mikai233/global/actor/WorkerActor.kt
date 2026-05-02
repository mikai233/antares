package com.mikai233.global.actor

import com.mikai233.common.message.Message
import com.mikai233.common.message.global.worker.HandoffWorker
import com.mikai233.global.GlobalNode
import io.github.mikai233.asteria.script.pekko.ScriptableAsteriaActor
import org.apache.pekko.actor.Props

class WorkerActor(val node: GlobalNode) : ScriptableAsteriaActor<GlobalNode>(node) {
    companion object {
        fun props(node: GlobalNode): Props = Props.create(WorkerActor::class.java, node)
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
            .match(HandoffWorker::class.java) { context.stop(self) }
            .match(Message::class.java) { handleMessage(it) }
            .build()
    }

    private fun handleMessage(message: Message) {
        logger.warning("WorkerActor received unsupported message: {}", message)
    }
}
