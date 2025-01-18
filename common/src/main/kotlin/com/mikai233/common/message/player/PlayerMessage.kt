package com.mikai233.common.message.player

import akka.actor.ActorRef
import akka.actor.NotInfluenceReceiveTimeout
import com.mikai233.common.message.Message
import com.mikai233.common.message.ShardMessage

interface PlayerMessage : Message, ShardMessage<Long> {
    val playerId: Long
    override val id: Long
        get() = playerId
}

data object HandoffPlayer : Message

data object PlayerInitialized : Message

data object PlayerUnloaded : Message

data object PlayerTick : Message, NotInfluenceReceiveTimeout

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
