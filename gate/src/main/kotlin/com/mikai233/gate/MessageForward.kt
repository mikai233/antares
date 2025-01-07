package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.MessageDispatcher
import com.mikai233.common.message.MessageHandler
import com.mikai233.protocol.idForClientMessage
import org.reflections.Reflections
import kotlin.reflect.full.declaredMemberFunctions

/**
 * @author mikai233
 * @date 2023/6/16
 */

enum class Forward {
    PlayerActor,
    WorldActor,
    ChannelActor,
}

object MessageForward {
    private val ForwardTarget = mutableMapOf(
        "com.mikai233.player.handler" to Forward.PlayerActor,
        "com.mikai233.world.handler" to Forward.WorldActor,
        "com.mikai233.gate.handler" to Forward.ChannelActor,
    )
    private val ForwardMap = mutableMapOf<Int, Forward>()

    fun whichActor(id: Int): Forward? {
        return ForwardMap[id]
    }

    fun addExtra(id: Int, target: Forward) {
        ForwardMap[id] = target
    }

    init {
        buildForwardMap()
    }

    private fun buildForwardMap() {
        ForwardTarget.forEach { (pkg, forward) ->
            Reflections(pkg).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
                clazz.kotlin.declaredMemberFunctions.forEach { kFunction ->
                    MessageDispatcher.processFunction(GeneratedMessage::class, kFunction) {
                        val id = idForClientMessage(it)
                        if (ForwardMap.containsKey(id)) {
                            error("proto id:$id already has a forward target")
                        }
                        ForwardMap[id] = forward
                    }
                }
            }
        }
    }
}
