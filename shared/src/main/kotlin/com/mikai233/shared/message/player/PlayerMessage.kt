package com.mikai233.shared.message.player

import com.mikai233.common.message.Message
import com.mikai233.common.script.Script
import com.mikai233.shared.message.PlayerMessage

data object HandoffPlayer : Message

data class PlayerScript(val script: Script) : Message

data object PlayerInitialized : Message

data object PlayerUnloaded : Message

data object PlayerTick : Message

data class PlayerLogin(
    val account: String,
    override val playerId: Long,
    val worldId: Long
) : PlayerMessage

data class PlayerCreate(
    val account: String,
    override val playerId: Long,
    val worldId: Long,
    val nickname: String,
) : PlayerMessage
