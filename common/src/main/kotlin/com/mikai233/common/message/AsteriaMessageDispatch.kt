package com.mikai233.common.message

import io.github.mikai233.asteria.core.NodeRuntime
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageDispatcher as AsteriaMessageDispatcher
import io.github.mikai233.asteria.message.MessageHandler as AsteriaMessageHandler
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

class ActorMessageDispatcher<A : Any, M : Any>(
    private val runtime: NodeRuntime,
) {
    private val registry = PatchableMessageHandlerRegistry<M>()
    private val dispatcher = AsteriaMessageDispatcher(registry)

    fun <T : M> register(
        messageType: KClass<T>,
        handler: (A, T) -> Unit,
    ) {
        registry.register(
            messageType,
            object : AsteriaMessageHandler<T> {
                override fun handle(context: HandlerContext, message: T) {
                    @Suppress("UNCHECKED_CAST")
                    handler((context as ActorHandlerContext<A>).actor, message)
                }
            },
        )
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

    fun <T : M> register(
        messageType: KClass<T>,
        handler: (A, S, T) -> Unit,
    ) {
        registry.register(
            messageType,
            object : AsteriaMessageHandler<T> {
                override fun handle(context: HandlerContext, message: T) {
                    @Suppress("UNCHECKED_CAST")
                    val typedContext = context as ActorSessionHandlerContext<A, S>
                    handler(typedContext.actor, typedContext.session, message)
                }
            },
        )
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

class ActorCommandDispatcher<A : Any> {
    private val handlers = mutableMapOf<String, (A, List<String>) -> Unit>()

    fun register(
        command: String,
        handler: (A, List<String>) -> Unit,
    ) {
        check(handlers.putIfAbsent(command, handler) == null) {
            "duplicate gm handler for command=$command"
        }
    }

    fun dispatch(
        actor: A,
        command: String,
        params: List<String>,
    ) {
        val handler = requireNotNull(handlers[command]) {
            "gm handler for command=$command not found"
        }
        handler(actor, params)
    }
}

class ActorSessionCommandDispatcher<A : Any, S : Any> {
    private val handlers = mutableMapOf<String, (A, S, List<String>) -> Unit>()

    fun register(
        command: String,
        handler: (A, S, List<String>) -> Unit,
    ) {
        check(handlers.putIfAbsent(command, handler) == null) {
            "duplicate gm handler for command=$command"
        }
    }

    fun dispatch(
        actor: A,
        session: S,
        command: String,
        params: List<String>,
    ) {
        val handler = requireNotNull(handlers[command]) {
            "gm handler for command=$command not found"
        }
        handler(actor, session, params)
    }
}
