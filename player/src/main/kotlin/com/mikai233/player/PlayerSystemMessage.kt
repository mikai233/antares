package com.mikai233.player

import akka.actor.typed.ActorRef
import com.mikai233.common.core.components.GuardianMessage
import com.mikai233.shared.message.ScriptMessage

sealed interface PlayerSystemMessage : GuardianMessage

data class LocalScriptActorReq(val replyTo: ActorRef<LocalScriptActorResp>) : PlayerSystemMessage

data class LocalScriptActorResp(val scriptActor: ActorRef<ScriptMessage>)