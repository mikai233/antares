package com.mikai233.common.ext

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
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

suspend fun <Req, Resp> ActorRef<Req>.ask(
    system: ActorSystem<Void>,
    requestFunction: (ActorRef<Resp>) -> Req,
    timeout: Duration = 3.minutes
): Resp {
    return AskPattern.ask<Req, Resp>(
        this,
        { replyTo -> requestFunction(replyTo) },
        timeout.toJavaDuration(),
        system.scheduler()
    ).await()
}

fun <Req, Resp> syncAsk(
    target: ActorRef<Req>,
    scheduler: Scheduler,
    timeout: Duration = 3.minutes,
    requestFunction: (ActorRef<Resp>) -> Req
): Resp {
    val completionStage = AskPattern.ask<Req, Resp>(
        target,
        { replyTo -> requestFunction(replyTo) },
        timeout.toJavaDuration(),
        scheduler
    )
    return completionStage.toCompletableFuture().get()
}