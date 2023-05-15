package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3

data class RunnableMessage(private val block: () -> Unit) : Runnable, ChannelMessage {
    override fun run() {
        block()
    }
}

data class ClientMessage(val message: GeneratedMessageV3) : ChannelMessage

data class GracefulShutdown(val reason: String) : ChannelMessage

data class Test(val name: String) : InternalChannelMessage