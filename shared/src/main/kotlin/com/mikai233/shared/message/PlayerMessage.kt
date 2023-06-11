package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.shared.script.ActorScriptFunction
import com.mikai233.shared.script.Script

object StopPlayer : SerdePlayerMessage

data class PlayerScript(val script: Script) : SerdePlayerMessage

object PlayerInitDone : PlayerMessage

object PlayerTick : PlayerMessage

data class ExecutePlayerScript(val script: ActorScriptFunction<in AbstractBehavior<*>>) : PlayerMessage

data class PlayerProtobufEnvelope(val inner: GeneratedMessageV3) : BusinessPlayerMessage

data class WHPlayerLogin(
    val channelActor: ActorRef<SerdeChannelMessage>,
    val account: String,
    val playerId: Long,
    val worldId: Long
) : BusinessPlayerMessage

data class WHPlayerCreate(
    val channelActor: ActorRef<SerdeChannelMessage>,
    val account: String,
    val playerId: Long,
    val worldId: Long,
    val nickname: String,
) : BusinessPlayerMessage
