package com.mikai233.common.core.actor

import org.apache.pekko.actor.AbstractActorWithTimers
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.TimerScheduler

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
