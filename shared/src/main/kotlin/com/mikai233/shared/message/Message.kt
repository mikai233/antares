package com.mikai233.shared.message

import com.mikai233.common.message.Message
import com.mikai233.common.message.ShardMessage

data class ExcelUpdate(val hashcode: Int) : Message

sealed interface ChannelMessage : Message

interface PlayerMessage : Message, ShardMessage<Long> {
    val playerId: Long
    override val id: Long
        get() = playerId
}

interface WorldMessage : Message, ShardMessage<Long> {
    val worldId: Long
    override val id: Long
        get() = worldId
}

sealed interface GlobalUidMessage : Message
