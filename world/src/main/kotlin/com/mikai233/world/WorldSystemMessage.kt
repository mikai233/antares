package com.mikai233.world

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.GuardianMessage
import com.mikai233.shared.message.ScriptMessage

sealed interface WorldSystemMessage : GuardianMessage

data class SpawnScriptActorReq(val replyTo: ActorRef<SpawnScriptActorResp>) : WorldSystemMessage

data class SpawnScriptActorResp(val scriptActor: ActorRef<ScriptMessage>)
