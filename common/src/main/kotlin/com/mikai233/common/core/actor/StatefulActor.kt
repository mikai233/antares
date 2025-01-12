package com.mikai233.common.core.actor

import akka.actor.AbstractActorWithStash
import akka.actor.ActorRef
import com.mikai233.common.core.Node
import com.mikai233.common.event.Event
import com.mikai233.common.extension.*
import com.mikai233.common.message.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import scala.PartialFunction
import scala.runtime.BoxedUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class StatefulActor<N>(val node: N) : AbstractActorWithStash() where N : Node {
    val logger = actorLogger()
    val coroutineScope = self.safeActorCoroutineScope()

    private lateinit var timers: ActorRef

    override fun preStart() {
        super.preStart()
        timers = context.actorOf(TimersActor.props(), "timers")
    }

    override fun aroundReceive(receive: PartialFunction<Any, BoxedUnit>?, msg: Any?) {
        when (msg) {
            is ActorCoroutineRunnable -> {
                handleRunnable<ActorCoroutineRunnable> { msg.run() }
            }

            is ActorNamedRunnable -> {
                handleRunnable<ActorNamedRunnable> { msg.block() }
            }

            is ExecuteActorScript -> {
                node.scriptActor.forward(CompileActorScript(msg.uid, msg.script, self), context)
            }

            is ExecuteActorFunction -> {
                try {
                    msg.function.invoke(this, msg.extra)
                    sender.tell(ExecuteScriptResult(msg.uid, true), self)
                } catch (e: Exception) {
                    sender.tell(ExecuteScriptResult(msg.uid, false), self)
                    logger.error(e, "{} failed to execute actor function", self)
                }
            }

            else -> {
                super.aroundReceive(receive, msg)
            }
        }
    }

    private inline fun <reified T> handleRunnable(runnable: () -> Unit) {
        try {
            runnable()
        } catch (e: Exception) {
            logger.error(e, "{} failed to execute {}", self.path(), T::class.java)
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

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        timeout: Duration? = 3.minutes,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return if (timeout == null) {
            coroutineScope.launch(context, start, block)
        } else {
            coroutineScope.launch(context, start) {
                withTimeout(timeout, block)
            }
        }
    }

    fun <T> async(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T
    ): Deferred<T> {
        return coroutineScope.async(context, start, block)
    }

    fun execute(name: String, block: () -> Unit) {
        self tell ActorNamedRunnable(name, block)
    }

    fun fireEvent(event: Event) {
        self tell event
    }
}