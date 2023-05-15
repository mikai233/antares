package com.mikai233

import akka.actor.typed.ActorRef
import com.mikai233.common.core.components.ClusterMessage
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.PlayerMessage
import io.netty.channel.ChannelHandlerContext

sealed interface GateSystemMessage : ClusterMessage {
}

data class SpawnChannelActorReq(
    val ctx: ChannelHandlerContext,
    val replyTo: ActorRef<SpawnChannelActorResp>,
    val player: ActorRef<PlayerMessage>
) :
    GateSystemMessage

data class SpawnChannelActorResp(val channelActor: ActorRef<ChannelMessage>)