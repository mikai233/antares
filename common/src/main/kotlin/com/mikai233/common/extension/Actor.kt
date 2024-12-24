package com.mikai233.common.extension

import akka.actor.*
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardCoordinator
import akka.cluster.sharding.ShardRegion
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.pattern.Patterns
import com.mikai233.common.core.Role
import com.mikai233.common.message.Message
import kotlinx.coroutines.future.await
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

fun AbstractActor.actorLogger(): LoggingAdapter {
    return Logging.getLogger(context.system, javaClass)
}

infix fun ActorRef.tell(message: Any) {
    this.tell(message, ActorRef.noSender())
}

@Suppress("UNCHECKED_CAST")
suspend fun <Req, Resp> ActorRef.ask(
    message: Any,
    timeout: Duration = 3.minutes
): Resp where Req : Message, Resp : Message {
    return Patterns.ask(this, message, timeout.toJavaDuration()).await() as Resp
}

@Suppress("UNCHECKED_CAST")
fun <Req, Resp> ActorRef.blockingAsk(
    message: Any,
    timeout: Duration = 3.minutes
): Resp where Req : Message, Resp : Message {
    return Patterns.ask(this, message, timeout.toJavaDuration()).toCompletableFuture()
        .get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) as Resp
}

fun ActorSystem.startSingleton(name: String, role: Role, props: Props, handoffMessage: Message): ActorRef {
    val settings = ClusterSingletonManagerSettings.create(this).withRole(role.name)
    val singletonProps = ClusterSingletonManager.props(props, handoffMessage, settings)
    return actorOf(singletonProps, name)
}

fun ActorSystem.startSingletonProxy(name: String, role: Role): ActorRef {
    val settings = ClusterSingletonProxySettings.create(this).withRole(role.name)
    return actorOf(ClusterSingletonProxy.props("/user/${name}", settings))
}

fun ActorSystem.startSharding(
    typename: String,
    role: Role,
    props: Props,
    handoffMessage: Message,
    extractor: ShardRegion.MessageExtractor,
    strategy: ShardCoordinator.ShardAllocationStrategy,
): ActorRef {
    val settings = ClusterShardingSettings.create(this).withRole(role.name)
    return ClusterSharding.get(this).start(typename, props, settings, extractor, strategy, handoffMessage)
}

fun ActorSystem.startShardingProxy(typename: String): ActorRef {
    return ClusterSharding.get(this).shardRegion(typename)
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
