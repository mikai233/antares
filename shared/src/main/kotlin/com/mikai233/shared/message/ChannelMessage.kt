package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3


data class ChannelProtobufEnvelope(val inner: GeneratedMessageV3) : SerdeChannelMessage

data class ChannelRunnable(private val block: () -> Unit) : Runnable, ChannelMessage {
    override fun run() {
        block()
    }
}

data class ClientMessage(val inner: GeneratedMessageV3) : ChannelMessage

data class StopChannel(val reason: StopReason) : ChannelMessage

enum class StopReason {
    ChannelInactive,
    UnexpectedMessage,
}

data class ChannelExpired(val reason: Int) : SerdeChannelMessage
