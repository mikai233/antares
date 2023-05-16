package com.mikai233

import akka.actor.typed.ActorRef
import com.mikai233.common.core.components.GuardianMessage
import com.mikai233.shared.message.ChannelMessage
import io.netty.channel.ChannelHandlerContext

sealed interface GateSystemMessage : GuardianMessage {
}

data class SpawnChannelActorReq(val ctx: ChannelHandlerContext, val replyTo: ActorRef<SpawnChannelActorResp>) :
    GateSystemMessage

data class SpawnChannelActorResp(val channelActorRef: ActorRef<ChannelMessage>)