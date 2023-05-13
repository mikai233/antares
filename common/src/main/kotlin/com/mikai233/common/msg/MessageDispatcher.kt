package com.mikai233.common.msg

import com.mikai233.common.ext.logger
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IgnoreHandleMe

data class MessageFunction(
    val clazz: KClass<out MessageHandler>,
    var handler: MessageHandler,
    val func: KFunction<*>,
)

class MessageDispatcher<M : Any>(private val message: KClass<M>, vararg packages: String) {
    private val logger = logger()
    private val packages = packages.toSet()
    private var handlers: Map<KClass<out MessageHandler>, MessageHandler>
    private val messages: Map<KClass<out M>, MessageFunction>

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

    private fun initMessages(): Map<KClass<out M>, MessageFunction> {
        val messages = mutableMapOf<KClass<out M>, MessageFunction>()
        handlers.forEach { (clazz, handler) ->
            clazz.declaredMemberFunctions.forEach funcForeach@{ mf ->
                if (mf.findAnnotation<IgnoreHandleMe>() != null) {
                    return@funcForeach
                }
                val kClassifier = message.createType().classifier!!
                mf.parameters.find { kp -> kp.type.isSubtypeOf(kClassifier.createType()) }?.let {
                    @Suppress("UNCHECKED_CAST") val key = it.type.classifier as KClass<out M>
                    check(
                        messages.containsKey(key).not()
                    ) { "duplicate message handle function:${key}, if this is not a handle function, add @IgnoreHandleMe to this function" }
                    messages[key] = MessageFunction(clazz, handler, mf)
                    logger.debug("add message handle function:{}", key)
                }
            }
        }
        return messages
    }

    fun dispatch(hint: KClass<out M>, vararg params: Any) {
        val methodFun = requireNotNull(messages[hint]) { "no message handler for:${hint} was found" }
        methodFun.func.call(methodFun.handler, *params)
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