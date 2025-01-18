package com.mikai233.common.message

import com.mikai233.common.extension.logger
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */

internal typealias K = KClass<out MessageHandler>

class MessageHandlerReflect(vararg packages: String) : Map<K, MessageHandler> {
    val logger = logger()

    @Volatile
    private var handlers: Map<K, MessageHandler>

    init {
        val handlers = mutableMapOf<K, MessageHandler>()
        Reflections(packages).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
            val kClass = clazz.kotlin
            if (!kClass.isAbstract) {
                val primaryConstructor = requireNotNull(kClass.primaryConstructor) {
                    "$kClass has no primary constructor"
                }
                handlers[kClass] = primaryConstructor.call()
            }
        }
        this.handlers = handlers
    }

    fun replace(k: K, newHandler: MessageHandler) {
        check(newHandler::class.isSubclassOf(k)) {
            "${newHandler::class.qualifiedName} must extends ${k.qualifiedName}"
        }
        check(handlers.containsKey(k)) {
            "previous handler not exists, " +
                "cannot replace handler:${k.qualifiedName} to ${newHandler::class.qualifiedName}"
        }
        val copiedHandlers = HashMap(handlers)
        copiedHandlers[k] = newHandler
        handlers = copiedHandlers
        logger.info("replace handler {} to {} success", k.qualifiedName, newHandler::class.qualifiedName)
    }

    inline fun <reified H> replace(newHandler: MessageHandler) where H : MessageHandler {
        replace(H::class, newHandler)
    }

    override val size: Int
        get() = handlers.size
    override val entries: Set<Map.Entry<K, MessageHandler>>
        get() = handlers.entries
    override val keys: Set<K>
        get() = handlers.keys
    override val values: Collection<MessageHandler>
        get() = handlers.values

    override fun get(key: K): MessageHandler? {
        return handlers[key]
    }

    override fun containsKey(key: K): Boolean {
        return handlers.containsKey(key)
    }

    override fun containsValue(value: MessageHandler): Boolean {
        return handlers.containsValue(value)
    }

    override fun isEmpty(): Boolean {
        return handlers.isEmpty()
    }
}
