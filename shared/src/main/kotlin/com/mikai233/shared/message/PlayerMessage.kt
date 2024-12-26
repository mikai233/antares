package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.script.Script

data object StopPlayer : SerdePlayerMessage

data class PlayerScript(val script: Script) : SerdePlayerMessage

data object PlayerInitDone : PlayerMessage

data object PlayerTick : PlayerMessage

data class PlayerProtobufEnvelope(val message: GeneratedMessage) : BusinessPlayerMessage

data class WHPlayerLogin(
    val account: String,
    val playerId: Long,
    val worldId: Long
) : BusinessPlayerMessage

data class WHPlayerCreate(
    val account: String,
    val playerId: Long,
    val worldId: Long,
    val nickname: String,
) : BusinessPlayerMessage
