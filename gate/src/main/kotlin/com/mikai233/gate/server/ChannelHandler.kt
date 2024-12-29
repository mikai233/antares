package com.mikai233.gate.server

import akka.actor.ActorRef
import com.mikai233.common.core.State
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.gate.ChannelActor
import com.mikai233.gate.GateNode
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ClientProtobuf
import com.mikai233.shared.message.StopChannel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey

const val CHANNEL_ACTOR_KEY = "CHANNEL_ACTOR_KEY"

@ChannelHandler.Sharable
class ChannelHandler(private val node: GateNode) : ChannelInboundHandlerAdapter() {
    private val actorKey = AttributeKey.valueOf<ActorRef>(CHANNEL_ACTOR_KEY)
    private val logger = logger()

    private fun tell(ctx: ChannelHandlerContext, message: ChannelMessage) {
        val channelActorRef: ActorRef? = ctx.channel().attr(actorKey).get()
        if (channelActorRef == null) {
            logger.warn("failed to send message:{} to channel actor because of channel actor not found", message)
        } else {
            channelActorRef.tell(message)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val state = node.state
        if (node.state == State.Started) {
            val channelActor = node.system.actorOf(ChannelActor.props(node, ctx))
            ctx.channel().attr(actorKey).set(channelActor)
        } else {
            logger.warn("gate is not running, current state:{}, channel will close", state)
            ctx.close()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        tell(ctx, StopChannel)
    }

    override fun channelRead(ctx: ChannelHandlerContext, message: Any) {
        if (message is ClientProtobuf) {
            tell(ctx, message)
        } else {
            logger.error("unsupported message:{}", message)
        }
    }
}
