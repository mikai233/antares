package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.Message
import com.mikai233.common.script.Script

data object StopPlayer : Message

data class PlayerScript(val script: Script) : Message

data object PlayerInitDone : Message

data object PlayerTick : Message

data class PlayerProtobufEnvelope(override val playerId: Long, val message: GeneratedMessage) : PlayerMessage

data class WHPlayerLogin(
    val account: String,
    override val playerId: Long,
    val worldId: Long
) : PlayerMessage

data class WHPlayerCreate(
    val account: String,
    override val playerId: Long,
    val worldId: Long,
    val nickname: String,
) : PlayerMessage
