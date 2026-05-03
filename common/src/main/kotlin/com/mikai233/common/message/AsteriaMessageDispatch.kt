package com.mikai233.common.message

import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageDispatcher as AsteriaMessageDispatcher
import kotlin.reflect.KClass

interface ActorHandlerContext<A : Any> : HandlerContext {
    val actor: A
}

data class DefaultActorHandlerContext<A : Any>(
    override val runtime: NodeRuntime,
    override val actor: A,
) : ActorHandlerContext<A>

fun <A : Any, M : Any> AsteriaMessageDispatcher<ActorHandlerContext<A>, M>.dispatchActor(
    runtime: NodeRuntime,
    actor: A,
    message: M,
) {
    dispatch(DefaultActorHandlerContext(runtime, actor), message)
}

fun <A : Any, M : Any> AsteriaMessageDispatcher<ActorHandlerContext<A>, M>.dispatchActor(
    runtime: NodeRuntime,
    actor: A,
    messageType: KClass<out M>,
    message: M,
) {
    dispatch(DefaultActorHandlerContext(runtime, actor), messageType, message)
}
