package com.mikai233.gate

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.message.IgnoreHandleMe
import com.mikai233.common.message.MessageHandler
import com.mikai233.protocol.MsgCs.MessageClientToServer
import com.mikai233.protocol.MsgSc.MessageServerToClient
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

/**
 * @author mikai233(dairch)
 * @date 2023/6/16
 */

enum class Forward {
    PlayerActor,
    WorldActor,
}

object MessageForward {
    private val forwardTarget = mutableMapOf<String, Forward>(
        "com.mikai233.player.handler" to Forward.PlayerActor,
        "com.mikai233.world.handler" to Forward.WorldActor,
    )
    private val forwardMap = mutableMapOf<Int, Forward>()

    fun whichActor(id: Int): Forward? {
        return forwardMap[id]
    }

    fun addExtra(id: Int, target: Forward) {
        forwardMap[id] = target
    }

    init {
        GlobalProto.init(MessageClientToServer.getDescriptor(), MessageServerToClient.getDescriptor())
        buildForwardMap()
    }

    private fun buildForwardMap() {
        forwardTarget.forEach { (pkg, target) ->
            Reflections(pkg).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
                clazz.kotlin.declaredMemberFunctions.forEach funcForeach@{ mf ->
                    if (mf.findAnnotation<IgnoreHandleMe>() != null) {
                        return@funcForeach
                    }
                    mf.parameters.find { kp ->
                        kp.type.isSubtypeOf(typeOf<GeneratedMessageV3>())
                    }?.let {
                        val protoType = it.type.classifier!!
                        @Suppress("UNCHECKED_CAST") val id =
                            GlobalProto.getClientMessageId(protoType as KClass<out GeneratedMessageV3>)
                        val mayExistsTarget = forwardMap[id]
                        if (mayExistsTarget != null) {
                            error("proto id$id already has a forward target")
                        }
                        forwardMap[id] = target
                    }
                }
            }
        }
    }
}
