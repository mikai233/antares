package com.mikai233.global

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.GuardianMessage
import com.mikai233.shared.message.ScriptMessage

sealed interface GlobalSystemMessage : GuardianMessage

data class SpawnScriptActorReq(val replyTo: ActorRef<SpawnScriptActorResp>) : GlobalSystemMessage

data class SpawnScriptActorResp(val scriptActor: ActorRef<ScriptMessage>)
