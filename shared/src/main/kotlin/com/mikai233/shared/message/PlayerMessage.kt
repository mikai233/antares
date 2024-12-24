package com.mikai233.shared.message

import akka.actor.AbstractActor
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.common.script.Script

object StopPlayer : SerdePlayerMessage

data class PlayerScript(val script: Script) : SerdePlayerMessage

object PlayerInitDone : PlayerMessage

object PlayerTick : PlayerMessage

data class ExecuteScript(val script: ActorScriptFunction<in AbstractActor>) : PlayerMessage

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
