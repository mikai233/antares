package com.mikai233.common.ext

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.*
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.cluster.routing.ClusterRouterGroup
import akka.cluster.routing.ClusterRouterGroupSettings
import akka.cluster.sharding.ShardCoordinator
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityContext
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.ClusterSingletonSettings
import akka.cluster.typed.SingletonActor
import akka.routing.BroadcastGroup
import com.mikai233.common.core.component.Role
import com.mikai233.common.msg.Message
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

inline fun <reified T> AbstractBehavior<T>.actorLogger(): Logger {
    return context.log
}

fun <T> AbstractBehavior<T>.runnableAdapter(block: (Runnable) -> T): ActorRef<Runnable> {
    return context.messageAdapter(Runnable::class.java, block)
}

infix fun <T> ActorRef<T>.tell(msg: T) {
    tell(msg)
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

//fun <T> ActorSystem<T>.startSingleton(
//    name: String,
//    role: Role,
//    props: Props,
//    handoffStopMessage: Any = Handoff,
//    settings: ClusterSingletonManagerSettings? = null
//): akka.actor.ActorRef {
//    val setting = settings ?: ClusterSingletonManagerSettings.create(this).withRole(role.name)
//    return actorOf(ClusterSingletonManager.props(props, handoffStopMessage, setting), name)
//}
//
//fun <T> ActorSystem<T>.startSingletonProxy(
//    role: ClusterRole,
//    path: String,
//    settings: ClusterSingletonProxySettings? = null
//): akka.actor.ActorRef {
//    val proxySetting = settings ?: ClusterSingletonProxySettings.create(this).withRole(role.name)
//    return actorOf(ClusterSingletonProxy.props(path, proxySetting))
//}

fun <T : Message> ActorSystem<*>.startSingleton(
    behavior: Behavior<T>,
    name: String,
    role: Role,
    settings: ClusterSingletonSettings? = null
): ActorRef<T> {
    val singleton = ClusterSingleton.get(this)
    val singletonSettings = settings ?: ClusterSingletonSettings.create(this).withRole(role.name)
    val singletonActor = SingletonActor.of(behavior, name).withSettings(singletonSettings)
    return singleton.init(singletonActor)
}

inline fun <reified M, N> ActorSystem<*>.startSharding(
    name: String,
    role: Role,
    extractor: ShardingMessageExtractor<ShardingEnvelope<out M>, M>,
    stopMessage: M,
    shardingSettings: ClusterShardingSettings? = null,
    noinline builder: (EntityContext<M>) -> Behavior<M>
): ActorRef<ShardingEnvelope<N>> where N : M {
    val sharding = ClusterSharding.get(this)
    val key = EntityTypeKey.create(M::class.java, name)
    val entity = Entity.of(key, builder)
        .withRole(role.name)
        .withMessageExtractor(extractor)
        .withAllocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(10, 3))
        .withStopMessage(stopMessage)
        .run {
            shardingSettings?.let {
                withSettings(shardingSettings)
            } ?: this
        }
    return sharding.init(entity).narrow()
}

inline fun <reified M, N> ActorSystem<*>.startShardingProxy(
    name: String,
    role: Role,
    extractor: ShardingMessageExtractor<ShardingEnvelope<out M>, M>
): ActorRef<ShardingEnvelope<N>> where N : M {
    val sharding = ClusterSharding.get(this)
    val key = EntityTypeKey.create(M::class.java, name)
    val entity = Entity.of(key) {
        Behaviors.empty()
    }
        .withRole(role.name)
        .withMessageExtractor(extractor)
    return sharding.init(entity).narrow()
}

inline fun <reified M> shardingEnvelope(entityId: String, message: M): ShardingEnvelope<M> {
    return ShardingEnvelope(entityId, message)
}

fun <T> ActorSystem<*>.registerService(key: ServiceKey<T>, service: ActorRef<T>) {
    receptionist().tell(Receptionist.register(key, service))
}

fun <T> ActorSystem<*>.deregisterService(key: ServiceKey<T>, service: ActorRef<T>) {
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
