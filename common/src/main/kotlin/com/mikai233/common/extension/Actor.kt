package com.mikai233.common.extension

import com.mikai233.protocol.ProtoCommon
import kotlinx.coroutines.future.await
import org.apache.pekko.actor.*
import org.apache.pekko.event.Logging
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.pattern.Patterns
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

fun AbstractActor.actorLogger(): LoggingAdapter {
    return Logging.getLogger(context.system, javaClass)
}

fun ActorRef.encodeActorRef(system: ActorSystem): ProtoCommon.ActorRef {
    return ProtoCommon.ActorRef.newBuilder()
        .setActorPath(path().toStringWithAddress(system.provider().defaultAddress))
        .build()
}

fun ProtoCommon.ActorRef.decodeActorRef(system: ActorSystem): ActorRef {
    check(actorPath.isNotBlank()) { "actor_path is blank" }
    return system.provider().resolveActorRef(actorPath)
}

infix fun ActorRef.tell(message: Any) {
    this.tell(message, ActorRef.noSender())
}

@Suppress("UNCHECKED_CAST")
suspend fun <R> ActorRef.ask(
    message: Any,
    timeout: Duration = 3.minutes,
): Result<R> {
    return runCatching { Patterns.ask(this, message, timeout.toJavaDuration()).await() as R }
}

@Suppress("UNCHECKED_CAST")
fun <R> ActorRef.blockingAsk(
    message: Any,
    timeout: Duration = 3.minutes,
): Result<R> {
    return runCatching {
        Patterns.ask(this, message, timeout.toJavaDuration()).toCompletableFuture()
            .get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) as R
    }
}

fun TimerScheduler.startSingleTimer(key: Any, message: Any, delay: Duration) {
    startSingleTimer(key, message, delay.toJavaDuration())
}

fun TimerScheduler.startTimerAtFixedRate(key: Any, message: Any, initialDelay: Duration, interval: Duration) {
    startTimerAtFixedRate(key, message, initialDelay.toJavaDuration(), interval.toJavaDuration())
}

fun TimerScheduler.startTimerAtFixedRate(key: Any, message: Any, interval: Duration) {
    startTimerAtFixedRate(key, message, interval.toJavaDuration())
}

fun TimerScheduler.startTimerWithFixedDelay(key: Any, message: Any, delay: Duration) {
    startTimerWithFixedDelay(key, message, delay.toJavaDuration())
}

fun TimerScheduler.startTimerWithFixedDelay(key: Any, message: Any, initialDelay: Duration, delay: Duration) {
    startTimerWithFixedDelay(key, message, initialDelay.toJavaDuration(), delay.toJavaDuration())
}
