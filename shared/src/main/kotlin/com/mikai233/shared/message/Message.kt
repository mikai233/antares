package com.mikai233.shared.message

import com.mikai233.common.msg.Message
import com.mikai233.common.serde.InternalMessage

sealed interface ChannelMessage : Message

object SayHello : ChannelMessage

object SayWorld : ChannelMessage

data class RunnableMessage(private val block: () -> Unit) : Runnable, ChannelMessage {
    override fun run() {
        block()
    }
}

sealed interface PlayerMessage : Message {
    var playerId: Long
}

sealed interface InternalPlayerMessage : PlayerMessage, InternalMessage

sealed interface WorldMessage : InternalMessage {
    var worldId: Long
}