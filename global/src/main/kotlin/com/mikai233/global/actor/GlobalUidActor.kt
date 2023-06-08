package com.mikai233.global.actor

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Receive
import com.mikai233.shared.message.GlobalUidMessage

class GlobalUidActor(context: ActorContext<GlobalUidMessage>) : AbstractBehavior<GlobalUidMessage>(context) {
    override fun createReceive(): Receive<GlobalUidMessage> {
        return newReceiveBuilder().build()
    }
}
