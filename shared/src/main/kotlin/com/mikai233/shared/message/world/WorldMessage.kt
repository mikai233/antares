package com.mikai233.shared.message.world

import com.mikai233.common.message.Message
import com.mikai233.protocol.ProtoLogin
import com.mikai233.shared.message.WorldMessage

data object HandoffWorld : Message

data class StopWorld(override val worldId: Long) : WorldMessage

data class WakeupWorld(override val worldId: Long) : WorldMessage

data object WorldTick : Message

data object WorldInitialized : Message

data object WorldUnloaded : Message

data class PlayerLogin(override val worldId: Long, val req: ProtoLogin.LoginReq) : WorldMessage
