package com.mikai233.common.message

import com.mikai233.common.extension.logger
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Handle

data class MessageHandle(
    val clazz: KClass<out MessageHandler>,
    var handler: MessageHandler,
    val handle: KFunction<Unit>,
) : KFunction<Unit> by handle

class MessageDispatcher<M : Any>(private val message: KClass<M>, vararg packages: String) {
    private val logger = logger()
    private val packages = packages.toSet()

    @Volatile
    private var handlers: Map<KClass<out MessageHandler>, MessageHandler>
    private val messages: Map<KClass<out M>, MessageHandle>

    init {
        handlers = initHandlers()
        messages = initMessages()
    }

    private fun initHandlers(): Map<KClass<out MessageHandler>, MessageHandler> {
        val handlers = mutableMapOf<KClass<out MessageHandler>, MessageHandler>()
        val cfg = ConfigurationBuilder.build(*packages.toTypedArray())
        Reflections(cfg).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
            val handler = clazz.getDeclaredConstructor().newInstance()
            handlers[clazz.kotlin] = handler
            logger.debug("add message handler:{}", clazz)
        }
        return handlers
    }

    private fun initMessages(): Map<KClass<out M>, MessageHandle> {
        val messages = mutableMapOf<KClass<out M>, MessageHandle>()
        handlers.forEach { (clazz, handler) ->
            clazz.declaredMemberFunctions.forEach { kFunction ->
                if (kFunction.findAnnotation<Handle>() != null) {
                    if (kFunction.parameters[2].type.isSubtypeOf(message.createType())) {
                        @Suppress("UNCHECKED_CAST")
                        val message = kFunction.parameters[2].type.classifier as KClass<out M>
                        check(!messages.containsKey(message)) { "duplicate message handle function for message:${message}" }
                        @Suppress("UNCHECKED_CAST")
                        messages[message] = MessageHandle(clazz, handler, kFunction as KFunction<Unit>)
                        logger.debug("add message handle function:{}", message)
                    }
                }
            }
        }
        return messages
    }

    fun dispatch(hint: KClass<out M>, vararg params: Any) {
        val messageHandle = messages[hint]
        if (messageHandle != null) {
            messageHandle.call(messageHandle.handler, *params)
        } else {
            logger.error("no message handler for:{} was found", hint)
        }
    }

    fun updateHandler(clazz: KClass<out MessageHandler>, newHandler: MessageHandler) {
        check(newHandler::class.isSubclassOf(clazz)) { "update handler:${newHandler::class} is not a subclass of $clazz" }
        check(handlers.containsKey(clazz)) { "previous handler not exists, cannot update handler:${clazz}" }
        val copiedHandlers = HashMap(handlers)
        copiedHandlers[clazz] = newHandler
        handlers = copiedHandlers
        messages.values.filter { it.clazz == clazz }.forEach {
            it.handler = newHandler
        }
        logger.info("update handler:{} success", clazz)
    }
}
