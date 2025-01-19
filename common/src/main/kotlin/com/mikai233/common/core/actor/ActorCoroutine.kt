package com.mikai233.common.core.actor

import akka.actor.ActorRef
import com.mikai233.common.annotation.Local
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Message
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Local
data class ActorCoroutineRunnable(val runnable: Runnable) : Message, Runnable by runnable

fun ActorRef.safeActorCoroutineScope(): TrackingCoroutineScope {
    val dispatcher = Executor { tell(ActorCoroutineRunnable(it)) }.asCoroutineDispatcher()
    return TrackingCoroutineScope(dispatcher + SupervisorJob())
}

class TrackingCoroutineScope(context: CoroutineContext) : CoroutineScope {
    private val job = Job(context[Job])
    override val coroutineContext: CoroutineContext = context + job

    private val jobs = ConcurrentHashMap.newKeySet<Job>()

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val newJob = (CoroutineScope::launch)(this, context, start, block)
        jobs.add(newJob)
        newJob.invokeOnCompletion { jobs.remove(newJob) }
        return newJob
    }

    fun getAllJobs(): Set<Job> = jobs
}
