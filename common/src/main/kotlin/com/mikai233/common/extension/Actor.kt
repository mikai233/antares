package com.mikai233.common.extension

import akka.actor.*
import akka.cluster.routing.ClusterRouterGroup
import akka.cluster.routing.ClusterRouterGroupSettings
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
import akka.routing.BroadcastGroup
import com.mikai233.common.core.component.Role
import com.mikai233.common.msg.Message
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

fun <M> ActorSystem<*>.publish(message: M) {
    eventStream().tell(EventStream.Publish(message))
}

inline fun <E, reified M> ActorSystem<*>.subscribe(subscriber: ActorRef<E>) where M : E {
    eventStream().tell(EventStream.Subscribe(M::class.java, subscriber.narrow<M>()))
}

inline fun <reified E> ActorSystem<*>.unsubscribe(subscriber: ActorRef<E>) {
    eventStream().tell(EventStream.Unsubscribe(subscriber))
}

inline fun <reified M> shardingEnvelope(entityId: String, message: M): ShardingEnvelope<M> {
    return ShardingEnvelope(entityId, message)
}

fun ActorSystem<*>.registerService(key: ServiceKey, service: ActorRef) {
    receptionist().tell(Receptionist.register(key, service))
}

fun ActorSystem<*>.deregisterService(key: ServiceKey, service: ActorRef) {
    receptionist().tell(Receptionist.deregister(key, service))
}

fun <M> ActorContext<*>.startBroadcastClusterRouterGroup(
    routeesPaths: Set<String>,
    useRoles: Set<Role>,
    totalInstances: Int = 10000
): ActorRef<M> {
    val group = ClusterRouterGroup(
        BroadcastGroup(routeesPaths),
        ClusterRouterGroupSettings(totalInstances, routeesPaths, true, useRoles.map { it.name }.toSet())
    )
    val ref = Adapter.actorOf(this, group.props())
    return Adapter.toTyped<M>(ref)
}

fun TimerScheduler.startPeriodicTimer(key: Any, message: Message, interval: Duration) {
    startPeriodicTimer(key, message, interval.toJavaDuration())
}

fun TimerScheduler.startSingleTimer(key: Any, message: Message, delay: Duration) {
    startSingleTimer(key, message, delay.toJavaDuration())
}

fun TimerScheduler.startSingleTimer(message: Any, delay: Duration) {
    startSingleTimer(message, delay.toJavaDuration())
}

fun TimerScheduler.startTimerAtFixedRate(key: Any, message: Message, initialDelay: Duration, interval: Duration) {
    startTimerAtFixedRate(key, message, initialDelay.toJavaDuration(), interval.toJavaDuration())
}

fun TimerScheduler.startTimerAtFixedRate(key: Any, message: Message, interval: Duration) {
    startTimerAtFixedRate(key, message, interval.toJavaDuration())
}

fun TimerScheduler.startTimerAtFixedRate(message: Message, interval: Duration) {
    startTimerAtFixedRate(message, interval.toJavaDuration())
}

fun TimerScheduler.startTimerAtFixedRate(message: Message, initialDelay: Duration, interval: Duration) {
    startTimerAtFixedRate(message, initialDelay.toJavaDuration(), interval.toJavaDuration())
}

fun TimerScheduler.startTimerWithFixedDelay(message: Message, delay: Duration) {
    startTimerWithFixedDelay(message, delay.toJavaDuration())
}

fun TimerScheduler.startTimerWithFixedDelay(key: Any, message: Message, delay: Duration) {
    startTimerWithFixedDelay(key, message, delay.toJavaDuration())
}

fun TimerScheduler.startTimerWithFixedDelay(message: Message, initialDelay: Duration, delay: Duration) {
    startTimerWithFixedDelay(message, initialDelay.toJavaDuration(), delay.toJavaDuration())
}

fun TimerScheduler.startTimerWithFixedDelay(key: Any, message: Message, initialDelay: Duration, delay: Duration) {
    startTimerWithFixedDelay(key, message, initialDelay.toJavaDuration(), delay.toJavaDuration())
}
