package com.mikai233.common.message.world

import com.mikai233.common.message.Message
import com.mikai233.common.message.ShardMessage
import com.mikai233.protocol.ProtoLogin

interface WorldMessage : Message, ShardMessage<Long> {
    val worldId: Long
    override val id: Long
        get() = worldId
}

data object HandoffWorld : Message

data class StopWorld(override val worldId: Long) : WorldMessage

data class WakeupWorldReq(override val worldId: Long) : WorldMessage

data object WakeupWorldResp : Message

data object WorldTick : Message

data object WorldInitialized : Message

data object WorldUnloaded : Message

data class PlayerLogin(override val worldId: Long, val req: ProtoLogin.LoginReq) : WorldMessage
