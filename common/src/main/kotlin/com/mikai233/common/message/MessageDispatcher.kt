package com.mikai233.common.message

import com.mikai233.common.annotation.Handle
import com.mikai233.common.extension.logger
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

private data class MessageHandle(
    val clazz: K,
    val handle: KFunction<Unit>,
    val size: Int,
) : KFunction<Unit> by handle

class MessageDispatcher<M : Any>(
    private val message: KClass<M>,
    private val handlerReflect: MessageHandlerReflect,
    private val receiverCount: Int,
) {
    companion object {
        fun <M : Any> processFunction(
            message: KClass<M>,
            kFunction: KFunction<*>,
            receiverCount: Int,
            block: (KClass<out M>) -> Unit,
        ) {
            val handle = kFunction.findAnnotation<Handle>()
            if (handle != null) {
                when (kFunction.parameters.size) {
                    1 + receiverCount -> {
                        //仅需要消息类型，不需要消息内容 查询Handle注解中的message属性
                        if (handle.message.isSubclassOf(message)) {
                            @Suppress("UNCHECKED_CAST")
                            block(handle.message as KClass<out M>)
                        }
                    }

                    2 + receiverCount -> {
                        //需要消息类型和消息内容
                        if (kFunction.parameters.last().type.isSubtypeOf(message.createType())) {
                            @Suppress("UNCHECKED_CAST")
                            block(kFunction.parameters.last().type.classifier as KClass<out M>)
                        }
                    }

                    else -> Unit
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
                processFunction(message, kFunction, receiverCount) { message ->
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

    fun dispatch(messageClazz: KClass<out M>, message: Any, vararg receiver: Any) {
        val messageHandle = handles[messageClazz]
        if (messageHandle != null) {
            val handler = requireNotNull(handlerReflect[messageHandle.clazz]) {
                "handler for ${messageHandle.clazz} not found"
            }
            if (messageHandle.size == receiver.size + 2) {
                messageHandle.call(handler, *receiver, message)
            } else {
                messageHandle.call(handler, *receiver)
            }
        } else {
            logger.error("no message handler for:{} was found", messageClazz)
        }
    }
}
