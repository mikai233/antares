package com.mikai233.gm

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.GuardianMessage
import com.mikai233.shared.message.ScriptMessage
import com.mikai233.shared.message.SerdeScriptMessage

sealed interface GmSystemMessage : GuardianMessage

data class SpawnScriptActorReq(val replyTo: ActorRef<SpawnScriptActorResp>) : GmSystemMessage

data class SpawnScriptActorResp(val scriptActor: ActorRef<ScriptMessage>)

data class SpawnScriptRouterReq(val replyTo: ActorRef<SpawnScriptRouterResp>) : GmSystemMessage

data class SpawnScriptRouterResp(val broadcastRouter: ActorRef<SerdeScriptMessage>)
