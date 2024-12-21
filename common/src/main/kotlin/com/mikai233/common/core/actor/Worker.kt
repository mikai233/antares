package com.mikai233.common.core.actor

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.extension.actorLogger
import com.mikai233.common.msg.Message

class Worker(context: ActorContext<Message>) : AbstractBehavior<Message>(context) {
    private val logger = actorLogger()

    init {
        logger.info("worker init")
    }

    override fun createReceive(): Receive<Message> {
        return newReceiveBuilder().onAnyMessage {
            logger.info("{}", it)
            Behaviors.same()
        }.build()
    }
}
