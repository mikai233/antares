package com.mikai233.common.core.actor

import akka.actor.AbstractActorWithStash
import akka.actor.ActorRef
import com.mikai233.common.core.Node
import com.mikai233.common.extension.*
import kotlinx.coroutines.channels.Channel
import scala.PartialFunction
import scala.runtime.BoxedUnit
import kotlin.time.Duration

abstract class StatefulActor<N>(val node: N) : AbstractActorWithStash() where N : Node {
    val logger = actorLogger()
    val coroutineScope = self.safeActorCoroutineScope()

    private lateinit var timers: ActorRef

    override fun preStart() {
        super.preStart()
        timers = context.actorOf(TimersActor.props(), "timers")
    }

    override fun aroundReceive(receive: PartialFunction<Any, BoxedUnit>?, msg: Any?) {
        if (msg is ActorCoroutineRunnable) {
            try {
                msg.run()
            } catch (e: Exception) {
                logger.error(e, "{} failed to execute {}", self.path(), ActorCoroutineRunnable::class.java)
            }
        } else {
            super.aroundReceive(receive, msg)
        }
    }

    fun cancel(key: Any) {
        val interaction = TimersInteraction { it.cancel(key) }
        timers.tell(interaction)
    }

    fun cancelAll() {
        val interaction = TimersInteraction { it.cancelAll() }
        timers.tell(interaction)
    }

    suspend fun isTimerActive(key: Any): Boolean {
        val channel = Channel<Boolean>(1)
        val interaction = TimersInteraction { channel.trySend(it.isTimerActive(key)) }
        timers.tell(interaction)
        return channel.receive()
    }

    fun startSingleTimer(key: Any, msg: Any, timeout: Duration) {
        val interaction = TimersInteraction { it.startSingleTimer(key, msg, timeout) }
        timers.tell(interaction)
    }

    fun startTimerAtFixedRate(key: Any, msg: Any, initialDelay: Duration, interval: Duration) {
        val interaction = TimersInteraction { it.startTimerAtFixedRate(key, msg, initialDelay, interval) }
        timers.tell(interaction)
    }

    fun startTimerAtFixedRate(key: Any, msg: Any, interval: Duration) {
        val interaction = TimersInteraction { it.startTimerAtFixedRate(key, msg, interval) }
        timers.tell(interaction)
    }

    fun startTimerWithFixedDelay(key: Any, msg: Any, initialDelay: Duration, delay: Duration) {
        val interaction = TimersInteraction { it.startTimerWithFixedDelay(key, msg, initialDelay, delay) }
        timers.tell(interaction)
    }

    fun startTimerWithFixedDelay(key: Any, msg: Any, delay: Duration) {
        val interaction = TimersInteraction { it.startTimerWithFixedDelay(key, msg, delay) }
        timers.tell(interaction)
    }
}