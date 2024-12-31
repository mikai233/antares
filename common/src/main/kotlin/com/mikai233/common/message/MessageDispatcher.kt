package com.mikai233.common.message

import com.mikai233.common.extension.logger
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

/**
 * @param message 消息类型，当且仅当需要处理此类型的消息，但是并不关心消息里面的内容时，才使用，这个时候handle function的参数应该只有一个
 * 如果handle function的参数有两个，那么[message]无效，会使用handle function的第二个参数的类型作为消息类型
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Handle(val message: KClass<out Any> = Any::class)

private data class MessageHandle(
    val clazz: KClass<out MessageHandler>,
    var handler: MessageHandler,
    val handle: KFunction<Unit>,
    val size: Int,
) : KFunction<Unit> by handle

class MessageDispatcher<M : Any>(private val message: KClass<M>, vararg packages: String) {
    companion object {
        fun <M : Any> processFunction(message: KClass<M>, kFunction: KFunction<*>, block: (KClass<out M>) -> Unit) {
            val handle = kFunction.findAnnotation<Handle>()
            if (handle != null) {
                when (kFunction.parameters.size) {
                    2 -> {
                        //仅需要消息类型，不需要消息内容 查询Handle注解中的message属性
                        if (handle.message.isSubclassOf(message)) {
                            @Suppress("UNCHECKED_CAST")
                            block(handle.message as KClass<out M>)
                        }
                    }

                    3 -> {
                        //需要消息类型和消息内容
                        if (kFunction.parameters[2].type.isSubtypeOf(message.createType())) {
                            @Suppress("UNCHECKED_CAST")
                            block(kFunction.parameters[2].type.classifier as KClass<out M>)
                        }
                    }

                    else -> {
                        error("message handle function:${kFunction.name} parameter count must be 1 or 2")
                    }
                }
            }
        }
    }

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
                processFunction(message, kFunction) { message ->
                    check(!messages.containsKey(message)) { "duplicate message handle function for message:${message}" }
                    @Suppress("UNCHECKED_CAST")
                    val handle = MessageHandle(clazz, handler, kFunction as KFunction<Unit>, kFunction.parameters.size)
                    messages[message] = handle
                    logger.debug("add message handle function:{}", message)
                }
            }
        }
        return messages
    }

    fun dispatch(messageClazz: KClass<out M>, receiver: Any, message: Any) {
        val messageHandle = messages[messageClazz]
        if (messageHandle != null) {
            if (messageHandle.size == 3) {
                messageHandle.call(messageHandle.handler, receiver, message)
            } else {
                messageHandle.call(messageHandle.handler, receiver)
            }
        } else {
            logger.error("no message handler for:{} was found", messageClazz)
        }
    }

    fun replaceHandler(clazz: KClass<out MessageHandler>, newHandler: MessageHandler) {
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
