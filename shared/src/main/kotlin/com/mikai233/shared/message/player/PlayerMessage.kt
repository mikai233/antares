package com.mikai233.shared.message.player

import akka.actor.ActorRef
import com.mikai233.common.message.Message
import com.mikai233.common.script.Script
import com.mikai233.shared.message.PlayerMessage

data object HandoffPlayer : Message

data class PlayerScript(val script: Script) : Message

data object PlayerInitialized : Message

data object PlayerUnloaded : Message

data object PlayerTick : Message

data class PlayerLoginReq(
    val account: String,
    override val playerId: Long,
    val worldId: Long,
    val channelActor: ActorRef,
) : PlayerMessage

data object PlayerLoginResp : Message

data class PlayerCreateReq(
    val account: String,
    override val playerId: Long,
    val worldId: Long,
    val nickname: String,
    val channelActor: ActorRef,
) : PlayerMessage

data object PlayerCreateResp : Message