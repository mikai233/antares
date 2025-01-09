package com.mikai233.common.message

import com.mikai233.common.extension.logger
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
    val clazz: K,
    val handle: KFunction<Unit>,
    val size: Int,
) : KFunction<Unit> by handle

class MessageDispatcher<M : Any>(private val message: KClass<M>, private val handlerReflect: MessageHandlerReflect) {
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

    // <MessageKClass, MessageHandle>
    private val handles: Map<KClass<out M>, MessageHandle>

    init {
        handles = initHandles()
    }

    private fun initHandles(): Map<KClass<out M>, MessageHandle> {
        val messages = mutableMapOf<KClass<out M>, MessageHandle>()
        handlerReflect.keys.forEach { clazz ->
            clazz.declaredMemberFunctions.forEach { kFunction ->
                processFunction(message, kFunction) { message ->
                    check(!messages.containsKey(message)) { "duplicate message handle function for message:${message}" }
                    @Suppress("UNCHECKED_CAST")
                    val handle = MessageHandle(clazz, kFunction as KFunction<Unit>, kFunction.parameters.size)
                    messages[message] = handle
                    logger.debug("add message handle function:{}", message)
                }
            }
        }
        return messages
    }

    fun dispatch(messageClazz: KClass<out M>, receiver: Any, message: Any) {
        val messageHandle = handles[messageClazz]
        if (messageHandle != null) {
            val handler = requireNotNull(handlerReflect[messageHandle.clazz]) {
                "handler for ${messageHandle.clazz} not found"
            }
            if (messageHandle.size == 3) {
                messageHandle.call(handler, receiver, message)
            } else {
                messageHandle.call(handler, receiver)
            }
        } else {
            logger.error("no message handler for:{} was found", messageClazz)
        }
    }
}
