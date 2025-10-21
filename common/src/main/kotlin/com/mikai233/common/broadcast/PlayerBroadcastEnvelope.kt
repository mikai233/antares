package com.mikai233.common.broadcast

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.Message

data class PlayerBroadcastEnvelope(
    val topic: String,
    val include: Set<Long>,
    val exclude: Set<Long>,
    val message: GeneratedMessage,
) : Message
