package com.mikai233.common.message

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.player.PlayerMessage
import com.mikai233.common.message.world.WorldMessage

data class PlayerProtobufEnvelope(override val playerId: Long, val message: GeneratedMessage) : PlayerMessage

data class WorldProtobufEnvelope(val playerId: Long, override val worldId: Long, val message: GeneratedMessage) :
    WorldMessage
