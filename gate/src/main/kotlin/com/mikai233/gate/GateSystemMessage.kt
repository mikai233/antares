package com.mikai233.gate

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.GuardianMessage
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ScriptMessage
import io.netty.channel.ChannelHandlerContext

sealed interface GateSystemMessage : GuardianMessage

data class SpawnChannelActorReq(val ctx: ChannelHandlerContext, val replyTo: ActorRef<SpawnChannelActorResp>) :
    GateSystemMessage

data class SpawnChannelActorResp(val channelActorRef: ActorRef<ChannelMessage>)

data class SpawnScriptActorReq(val replyTo: ActorRef<SpawnScriptActorResp>) : GateSystemMessage

data class SpawnScriptActorResp(val scriptActor: ActorRef<ScriptMessage>)
