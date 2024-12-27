package com.mikai233.shared.message

import com.mikai233.common.message.Message

data class ActorNamedRunnable(
    val name: String,
    val block: () -> Unit
) : Runnable, Message {
    override fun run() {
        block()
    }
}

data class ExcelUpdate(val hashcode: Int) : Message

sealed interface ChannelMessage : Message

interface PlayerMessage : Message {
    val playerId: Long
}

interface WorldMessage : Message {
    val worldId: Long
}

sealed interface GlobalUidMessage : Message
