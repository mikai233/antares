package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3

data class ClientMessage(val message: GeneratedMessageV3) : ChannelMessage

data class GracefulShutdown(val reason: String) : ChannelMessage