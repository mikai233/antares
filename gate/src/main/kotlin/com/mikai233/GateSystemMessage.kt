package com.mikai233

import akka.actor.typed.ActorRef
import com.mikai233.common.core.components.ClusterMessage
import com.mikai233.shared.message.ChannelMessage
import io.netty.channel.ChannelHandlerContext

sealed interface GateSystemMessage : ClusterMessage {
}

data class SpawnChannelActorReq(val ctx: ChannelHandlerContext, val replyTo: ActorRef<SpawnChannelActorResp>) :
    GateSystemMessage

data class SpawnChannelActorResp(val channelActor: ActorRef<ChannelMessage>)