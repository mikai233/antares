package com.mikai233.player

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.GuardianMessage
import com.mikai233.shared.message.ScriptMessage

sealed interface PlayerSystemMessage : GuardianMessage

data class SpawnScriptActorReq(val replyTo: ActorRef<SpawnScriptActorResp>) : PlayerSystemMessage

data class SpawnScriptActorResp(val scriptActor: ActorRef<ScriptMessage>)
