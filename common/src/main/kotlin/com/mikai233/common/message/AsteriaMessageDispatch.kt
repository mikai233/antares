package com.mikai233.common.message

import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler
import io.github.realmlabs.asteria.message.MessageDispatcher as AsteriaMessageDispatcher
import io.github.realmlabs.asteria.message.PatchableMessageHandlerRegistry
import kotlin.reflect.KClass

interface ActorHandlerContext<A : Any> : HandlerContext {
    val actor: A
}

data class DefaultActorHandlerContext<A : Any>(
    override val runtime: NodeRuntime,
    override val actor: A,
) : ActorHandlerContext<A>

inline fun <reified A : Any> HandlerContext.requireActor(): A {
    val actor = (this as? ActorHandlerContext<*>)?.actor
        ?: error("handler context does not contain actor: ${this::class.qualifiedName}")
    return actor as? A ?: error("expected actor ${A::class.qualifiedName} but got ${actor::class.qualifiedName}")
}

class ActorMessageDispatcher<A : Any, M : Any>(
    private val runtime: NodeRuntime,
) {
    private val registry = PatchableMessageHandlerRegistry<M>()
    private val dispatcher = AsteriaMessageDispatcher(registry)

    @Suppress("UNCHECKED_CAST")
    private fun actorOf(context: HandlerContext): A = (context as? ActorHandlerContext<*>)?.actor as? A
        ?: error("handler context does not contain actor: ${context::class.qualifiedName}")

    fun <T : M> register(
        messageType: KClass<T>,
        handler: (A, T) -> Unit,
    ) {
        register(
            messageType,
            MessageHandler { context, message ->
                handler(actorOf(context), message)
            },
        )
    }

    fun <T : M> register(
        messageType: KClass<T>,
        handler: MessageHandler<T>,
    ) {
        registry.register(messageType, handler)
    }

    inline fun <reified T : M> register(noinline handler: (A, T) -> Unit) {
        register(T::class, handler)
    }

    fun dispatch(
        actor: A,
        message: M,
    ) {
        dispatcher.dispatch(DefaultActorHandlerContext(runtime, actor), message)
    }

    fun dispatch(
        actor: A,
        messageType: KClass<out M>,
        message: M,
    ) {
        dispatcher.dispatch(DefaultActorHandlerContext(runtime, actor), messageType, message)
    }
}
