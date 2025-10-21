package com.mikai233.gate

import com.google.common.io.Resources
import com.mikai233.common.extension.Json
import com.mikai233.common.message.Forward
import com.mikai233.common.message.ForwardMap

/**
 * @author mikai233
 * @date 2023/6/16
 */
object MessageForward {

    private val ProtoForwardMap = mutableMapOf<Int, Forward>()
    private val CommandForwardMap = mutableMapOf<String, Forward>()

    fun whichActor(id: Int): Forward? {
        return ProtoForwardMap[id]
    }

    fun whichCommand(command: String): Forward? {
        return CommandForwardMap[command]
    }

    init {
        Forward.entries.forEach { actor ->
            val url = Resources.getResource("${actor.name}.json")
            val forwardMap = Json.fromBytes<ForwardMap>(url.openStream().readBytes())
            forwardMap.protos.forEach { id ->
                ProtoForwardMap[id] = actor
            }
            forwardMap.commands.forEach { command ->
                CommandForwardMap[command] = actor
            }
        }
    }
}
