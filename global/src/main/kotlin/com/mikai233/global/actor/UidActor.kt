package com.mikai233.global.actor

import akka.actor.Props
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.message.Message
import com.mikai233.global.GlobalNode
import com.mikai233.global.data.WorldUidMem
import com.mikai233.shared.message.global.uid.HandoffUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UidActor(node: GlobalNode) : StatefulActor<GlobalNode>(node) {
    companion object {
        fun props(node: GlobalNode): Props = Props.create(UidActor::class.java, node)
    }

    val uidMem = WorldUidMem { node.mongoDB.mongoTemplate }

    override fun preStart() {
        super.preStart()
        logger.info("UidActor[{}] started", self)
        launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    uidMem.init()
                }
            }
                .onFailure {
                    logger.error(it, "UidActor[{}] init failed", self)
                    context.stop(self)
                }
                .onSuccess {
                    unstashAll()
                    context.become(active())
                }
        }
    }

    override fun postStop() {
        super.postStop()
        logger.info("UidActor[{}] stopped", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(HandoffUid::class.java) { context.stop(self) }
            .matchAny { stash() }
            .build()
    }

    private fun active(): Receive {
        return receiveBuilder()
            .match(HandoffUid::class.java) { context.stop(self) }
            .match(Message::class.java) { handleUidMessage(it) }
            .build()
    }


    private fun handleUidMessage(message: Message) {
        try {
            node.internalDispatcher.dispatch(message::class, this, message)
        } catch (e: Exception) {
            logger.error(e, "uidActor handle message:{} failed", message)
        }
    }
}
