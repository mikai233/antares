package com.mikai233.gate.server

import akka.actor.typed.ActorRef
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.State
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.syncAsk
import com.mikai233.gate.GateNode
import com.mikai233.gate.SpawnChannelActorReq
import com.mikai233.gate.SpawnChannelActorResp
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ClientMessage
import com.mikai233.shared.message.GracefulShutdown
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey

@ChannelHandler.Sharable
class ChannelHandler(private val gateNode: GateNode) : ChannelInboundHandlerAdapter() {
    companion object {
        const val CHANNEL_ACTOR_KEY = "CHANNEL_ACTOR_KEY"
    }

    private val actorKey = AttributeKey.valueOf<ActorRef<ChannelMessage>>(CHANNEL_ACTOR_KEY)
    private val logger = logger()

    private fun tell(ctx: ChannelHandlerContext, message: ChannelMessage) {
        val channelActorRef: ActorRef<ChannelMessage>? = ctx.channel().attr(actorKey).get()
        if (channelActorRef == null) {
            logger.warn("failed to send message:{} to channel actor because of channel actor not found", message)
        } else {
            channelActorRef.tell(message)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val state = gateNode.server.state
        if (gateNode.server.state == State.Running) {
            val actorRef = spawnChannelActor(ctx)
            ctx.channel().attr(actorKey).set(actorRef)
        } else {
            logger.warn("gate is not running, current state:{}, channel will close", state)
            ctx.close()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        tell(ctx, GracefulShutdown("channelInactive"))
    }

    override fun channelRead(ctx: ChannelHandlerContext, message: Any) {
        if (message is GeneratedMessageV3) {
            tell(ctx, ClientMessage(message))
        } else {
            logger.error("unsupported message:{}", message)
        }
    }

    private fun spawnChannelActor(ctx: ChannelHandlerContext): ActorRef<ChannelMessage> {
        val system = gateNode.system()
        val resp: SpawnChannelActorResp = syncAsk(system, system.scheduler()) {
            SpawnChannelActorReq(ctx, it)
        }
        return resp.channelActorRef
    }
}