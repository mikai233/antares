package com.mikai233.world

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Receive
import com.mikai233.shared.message.WorldMessage

class WorldActor(context: ActorContext<WorldMessage>) : AbstractBehavior<WorldMessage>(context) {
    override fun createReceive(): Receive<WorldMessage> {
        TODO("Not yet implemented")
    }
}