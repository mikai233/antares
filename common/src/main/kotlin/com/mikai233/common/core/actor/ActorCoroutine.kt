package com.mikai233.common.core.actor

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


fun ActorRef<Runnable>.safeActorCoroutine(): CoroutineScope {
    return CoroutineScope(Executor { tell(it) }.asCoroutineDispatcher())
}

fun <T> AbstractBehavior<T>.unsafeActorCoroutine(): CoroutineScope {
    return CoroutineScope(context.executionContext.asCoroutineDispatcher())
}

class ActorCoroutine(private val scope: CoroutineScope) {
    private val jobs: ArrayList<Job> = arrayListOf()
    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val job = scope.launch(context + SupervisorJob(), start, block)
        jobs.add(job)
        return job
    }

    fun cancelAll(reason: String) {
        jobs.forEach {
            it.cancel(CancellationException(reason))
        }
    }
}
