package com.mikai233.global.actor

import akka.actor.AbstractActor

class GlobalUidActor : AbstractActor() {
    override fun createReceive(): Receive {
        return receiveBuilder().build()
    }
}
