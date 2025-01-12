package com.mikai233.common.message

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.player.PlayerMessage
import com.mikai233.common.message.world.WorldMessage

data class ProtobufEnvelope(override val id: Long, val message: GeneratedMessage) : PlayerMessage, WorldMessage {
    override val playerId: Long
        get() = id
    override val worldId: Long
        get() = id
}
