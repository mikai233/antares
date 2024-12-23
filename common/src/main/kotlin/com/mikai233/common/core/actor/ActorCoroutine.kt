package com.mikai233.common.core.actor

import akka.actor.ActorRef
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun ActorRef.safeActorCoroutine(): CoroutineScope {
    return CoroutineScope(Executor { tell(it, ActorRef.noSender()) }.asCoroutineDispatcher())
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
