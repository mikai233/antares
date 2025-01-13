package com.mikai233.gate

import com.google.common.io.Resources
import com.mikai233.common.extension.Json
import com.mikai233.common.message.Forward

/**
 * @author mikai233
 * @date 2023/6/16
 */
object MessageForward {

    private val ForwardMap = mutableMapOf<Int, Forward>()

    fun whichActor(id: Int): Forward? {
        return ForwardMap[id]
    }

    init {
        Forward.entries.forEach { actor ->
            val url = Resources.getResource("${actor.name}.json")
            Json.fromBytes<List<Int>>(url.openStream().readBytes()).forEach { id ->
                ForwardMap[id] = actor
            }
        }
    }

}
