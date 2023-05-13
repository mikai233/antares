package com.mikai233.common.ext

import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.AskPattern
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

inline fun <reified T> AbstractBehavior<T>.actorLogger(): Logger {
    return context.log
}

suspend fun <Req : M, Resp, M> ask(
    target: ActorRef<M>,
    scheduler: Scheduler,
    timeout: Duration = 3.minutes,
    requestFunction: (ActorRef<Resp>) -> Req
): Resp {
    return AskPattern.ask<M, Resp>(
        target,
        { replyTo -> requestFunction(replyTo) },
        timeout.toJavaDuration(),
        scheduler,
    ).await()
}

fun <Req : M, Resp, M> syncAsk(
    target: ActorRef<M>,
    scheduler: Scheduler,
    timeout: Duration = 3.minutes,
    requestFunction: (ActorRef<Resp>) -> Req
): Resp {
    val completionStage = AskPattern.ask<M, Resp>(
        target,
        { replyTo -> requestFunction(replyTo) },
        timeout.toJavaDuration(),
        scheduler
    )
    return completionStage.toCompletableFuture().get()
}