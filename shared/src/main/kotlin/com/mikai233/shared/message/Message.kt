package com.mikai233.shared.message

import com.mikai233.common.serde.InternalMessage

sealed interface ChannelMessage : InternalMessage

object SayHello : ChannelMessage

object SayWorld : ChannelMessage

data class RunnableMessage(private val block: () -> Unit) : Runnable, ChannelMessage {
    override fun run() {
        block()
    }
}