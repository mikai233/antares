package com.mikai233.common.message

import com.mikai233.common.extension.logger
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Gm(val command: String)

private data class GmHandle(
    val clazz: K,
    val handle: KFunction<Unit>,
) : KFunction<Unit> by handle

class GmDispatcher(private val handlerReflect: MessageHandlerReflect) {
    val logger = logger()

    private val handles: Map<String, GmHandle>

    init {
        this.handles = initHandles()
    }

    private fun initHandles(): Map<String, GmHandle> {
        val handles = mutableMapOf<String, GmHandle>()
        handlerReflect.keys.forEach { clazz ->
            clazz.declaredMemberFunctions.forEach { kFunction ->
                val gmAnnotation = kFunction.findAnnotation<Gm>()
                if (gmAnnotation != null) {
                    @Suppress("UNCHECKED_CAST")
                    val handle = GmHandle(clazz, kFunction as KFunction<Unit>)
                    check(!handles.containsKey(gmAnnotation.command)) {
                        "duplicate handle function for gm:${gmAnnotation.command}"
                    }
                    handles[gmAnnotation.command] = handle
                }
            }
        }
        return handles
    }

    fun dispatch(command: String, receiver: Any, params: List<String>) {
        val commandHandle = handles[command]
        if (commandHandle != null) {
            val handler = requireNotNull(handlerReflect[commandHandle.clazz]) {
                "handler for ${commandHandle.clazz} not found"
            }
            if (commandHandle.handle.parameters.size == 3) {
                commandHandle.call(handler, receiver, params)
            } else {
                commandHandle.call(handler, receiver)
            }
        } else {
            logger.error("no gm handler for command: {} was found", command)
        }
    }
}
