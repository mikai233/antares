package com.mikai233.common.core.actor

import akka.actor.AbstractActorWithTimers
import akka.actor.Props
import akka.actor.TimerScheduler

data class TimersInteraction(val block: (TimerScheduler) -> Unit)

class TimersActor : AbstractActorWithTimers() {
    companion object {
        fun props(): Props = Props.create(TimersActor::class.java)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(TimersInteraction::class.java) { it.block(timers) }
            .matchAny { context.parent.tell(it, self) }
            .build()
    }
}
