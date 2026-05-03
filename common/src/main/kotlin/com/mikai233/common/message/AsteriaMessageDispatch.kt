package com.mikai233.common.message

import io.github.mikai233.asteria.core.NodeRuntime
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageDispatcher as AsteriaMessageDispatcher
import io.github.mikai233.asteria.message.MessageHandler
import io.github.mikai233.asteria.message.PatchableMessageHandlerRegistry
import kotlin.reflect.KClass

data class ActorHandlerContext<A : Any>(
    override val runtime: NodeRuntime,
    val actor: A,
) : HandlerContext

data class ActorSessionHandlerContext<A : Any, S : Any>(
    override val runtime: NodeRuntime,
    val actor: A,
    val session: S,
) : HandlerContext

inline fun <reified A : Any> HandlerContext.requireActor(): A {
    val actor = when (this) {
        is ActorHandlerContext<*> -> actor
        is ActorSessionHandlerContext<*, *> -> actor
        else -> error("handler context does not contain actor: ${this::class.qualifiedName}")
    }
    return actor as? A ?: error("expected actor ${A::class.qualifiedName} but got ${actor::class.qualifiedName}")
}

inline fun <reified S : Any> HandlerContext.requireSession(): S {
    val session = (this as? ActorSessionHandlerContext<*, *>)?.session
        ?: error("handler context does not contain session: ${this::class.qualifiedName}")
    return session as? S ?: error("expected session ${S::class.qualifiedName} but got ${session::class.qualifiedName}")
}
class ActorMessageDispatcher<A : Any, M : Any>(
    private val runtime: NodeRuntime,
) {
    private val registry = PatchableMessageHandlerRegistry<M>()
    private val dispatcher = AsteriaMessageDispatcher(registry)

    @Suppress("UNCHECKED_CAST")
    private fun actorOf(context: HandlerContext): A = when (context) {
        is ActorHandlerContext<*> -> context.actor as A
        is ActorSessionHandlerContext<*, *> -> context.actor as A
        else -> error("handler context does not contain actor: ${context::class.qualifiedName}")
    }

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
        dispatcher.dispatch(ActorHandlerContext(runtime, actor), message)
    }

    fun dispatch(
        actor: A,
        messageType: KClass<out M>,
        message: M,
    ) {
        dispatcher.dispatch(ActorHandlerContext(runtime, actor), messageType, message)
    }
}

class ActorSessionMessageDispatcher<A : Any, S : Any, M : Any>(
    private val runtime: NodeRuntime,
) {
    private val registry = PatchableMessageHandlerRegistry<M>()
    private val dispatcher = AsteriaMessageDispatcher(registry)

    @Suppress("UNCHECKED_CAST")
    private fun actorOf(context: HandlerContext): A = (context as? ActorSessionHandlerContext<*, *>)?.actor as? A
        ?: error("handler context does not contain actor: ${context::class.qualifiedName}")

    @Suppress("UNCHECKED_CAST")
    private fun sessionOf(context: HandlerContext): S = (context as? ActorSessionHandlerContext<*, *>)?.session as? S
        ?: error("handler context does not contain session: ${context::class.qualifiedName}")

    fun <T : M> register(
        messageType: KClass<T>,
        handler: (A, S, T) -> Unit,
    ) {
        register(
            messageType,
            MessageHandler { context, message ->
                handler(actorOf(context), sessionOf(context), message)
            },
        )
    }

    fun <T : M> register(
        messageType: KClass<T>,
        handler: MessageHandler<T>,
    ) {
        registry.register(messageType, handler)
    }

    inline fun <reified T : M> register(noinline handler: (A, S, T) -> Unit) {
        register(T::class, handler)
    }

    fun dispatch(
        actor: A,
        session: S,
        message: M,
    ) {
        dispatcher.dispatch(ActorSessionHandlerContext(runtime, actor, session), message)
    }

    fun dispatch(
        actor: A,
        session: S,
        messageType: KClass<out M>,
        message: M,
    ) {
        dispatcher.dispatch(ActorSessionHandlerContext(runtime, actor, session), messageType, message)
    }
}
