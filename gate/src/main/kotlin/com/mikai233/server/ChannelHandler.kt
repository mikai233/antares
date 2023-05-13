package com.mikai233.server

import akka.actor.typed.ActorRef
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.Gate
import com.mikai233.SpawnChannelActorAns
import com.mikai233.SpawnChannelActorAsk
import com.mikai233.common.core.State
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.syncAsk
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ClientMessage
import com.mikai233.shared.message.GracefulShutdown
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey

@ChannelHandler.Sharable
class ChannelHandler(private val gate: Gate) : ChannelInboundHandlerAdapter() {
    companion object {
        const val CHANNEL_ACTOR_KEY = "CHANNEL_ACTOR_KEY"
    }

    private val actorKey = AttributeKey.valueOf<ActorRef<ChannelMessage>>(CHANNEL_ACTOR_KEY)
    private val logger = logger()

    private fun tell(ctx: ChannelHandlerContext, message: ChannelMessage) {
        val channelActor: ActorRef<ChannelMessage>? = ctx.channel().attr(actorKey).get()
        if (channelActor == null) {
            logger.warn("failed to send message:{} to channel actor because of channel actor not found", message)
        } else {
            channelActor.tell(message)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val state = gate.server.serverState()
        if (state == State.Running) {
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
        val system = gate.system()
        val spawnChannelActorAns: SpawnChannelActorAns = syncAsk(system, system.scheduler()) {
            SpawnChannelActorAsk(ctx, it)
        }
        return spawnChannelActorAns.channelActor
    }
}