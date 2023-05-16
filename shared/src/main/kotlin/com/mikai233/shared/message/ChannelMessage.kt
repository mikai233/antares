package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3


data class ChannelProtobufEnvelope(val message: GeneratedMessageV3) : SerdeChannelMessage

data class ChannelRunnable(private val block: () -> Unit) : Runnable, ChannelMessage {
    override fun run() {
        block()
    }
}

data class ClientMessage(val message: GeneratedMessageV3) : ChannelMessage

data class GracefulShutdown(val reason: String) : ChannelMessage

data class Test(val name: String) : SerdeChannelMessage