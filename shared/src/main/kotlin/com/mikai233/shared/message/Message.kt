package com.mikai233.shared.message

import com.mikai233.common.msg.Message
import com.mikai233.common.serde.InternalMessage

sealed interface ChannelMessage : Message

sealed interface InternalChannelMessage : ChannelMessage, InternalMessage

sealed interface PlayerMessage : Message {
    var playerId: Long
}

sealed interface InternalPlayerMessage : PlayerMessage, InternalMessage

sealed interface WorldMessage : InternalMessage {
    var worldId: Long
}