package com.mikai233.common.ext

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.AskPattern
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.ShardCoordinator
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityContext
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import com.mikai233.common.core.components.Role
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

inline fun <reified M, N> ActorSystem<*>.startSharding(
    name: String,
    role: Role,
    extractor: ShardingMessageExtractor<ShardingEnvelope<out M>, M>,
    stopMessage: M,
    noinline builder: (EntityContext<M>) -> Behavior<M>
): ActorRef<ShardingEnvelope<N>> where N : M {
    val sharding = ClusterSharding.get(this)
    val key = EntityTypeKey.create(M::class.java, name)
    val entity = Entity.of(key, builder)
        .withRole(role.name)
        .withMessageExtractor(extractor)
        .withAllocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(10, 3))
        .withStopMessage(stopMessage)
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