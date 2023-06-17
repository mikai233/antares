package com.mikai233.gate

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.msg.IgnoreHandleMe
import com.mikai233.common.msg.MessageHandler
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
    val forwardTarget = mutableSetOf("com.mikai233.player.handler", "com.mikai233.world.handler")
    val forwardMap = mutableMapOf<Int, Forward>()

    fun whichActor(id: Int): Forward {
        TODO()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        GlobalProto.init(MessageClientToServer.getDescriptor(), MessageServerToClient.getDescriptor())
        Reflections(forwardTarget).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
            clazz.kotlin.declaredMemberFunctions.forEach funcForeach@{ mf ->
                if (mf.findAnnotation<IgnoreHandleMe>() != null) {
                    return@funcForeach
                }
                mf.parameters.find { kp ->
                    kp.type.isSubtypeOf(typeOf<GeneratedMessageV3>()) }?.let {
                    val protoType = it.type.classifier!!
                    val id = GlobalProto.getClientMessageId(protoType as KClass<out GeneratedMessageV3>)
                    println(id)
                }
            }
        }
    }
}