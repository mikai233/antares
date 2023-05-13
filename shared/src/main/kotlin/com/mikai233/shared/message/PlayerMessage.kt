package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3

data class ClientToPlayerMessage(val message: GeneratedMessageV3, override var playerId: Long) : PlayerMessage