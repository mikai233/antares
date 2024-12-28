package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessage

data class ProtobufEnvelope(override val id: Long, val message: GeneratedMessage) : PlayerMessage, WorldMessage {
    override val playerId: Long
        get() = id
    override val worldId: Long
        get() = id
}
