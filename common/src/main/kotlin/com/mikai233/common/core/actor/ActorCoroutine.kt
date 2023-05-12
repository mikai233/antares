package com.mikai233.common.core.actor

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor


fun ActorRef<Runnable>.safeActorCoroutine(): CoroutineScope {
    return CoroutineScope(Executor { tell(it) }.asCoroutineDispatcher())
}

fun <T> AbstractBehavior<T>.unsafeActorCoroutine(): CoroutineScope {
    return CoroutineScope(context.executionContext.asCoroutineDispatcher())
}