package com.mikai233.gate.server

import akka.actor.typed.ActorRef
import com.mikai233.common.core.Server
import com.mikai233.common.core.State
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.syncAsk
import com.mikai233.common.inject.XKoin
import com.mikai233.gate.GateSystemMessage
import com.mikai233.gate.SpawnChannelActorReq
import com.mikai233.gate.SpawnChannelActorResp
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ClientMessage
import com.mikai233.shared.message.StopChannel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@ChannelHandler.Sharable
class ChannelHandler(private val koin: XKoin) : ChannelInboundHandlerAdapter(), KoinComponent by koin {
    companion object {
        const val CHANNEL_ACTOR_KEY = "CHANNEL_ACTOR_KEY"
    }

    private val server by inject<Server>()
    private val akkaSystem by inject<AkkaSystem<GateSystemMessage>>()
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
        val state = server.state
        if (server.state == State.Running) {
            val actorRef = spawnChannelActor(ctx)
            ctx.channel().attr(actorKey).set(actorRef)
        } else {
            logger.warn("gate is not running, current state:{}, channel will close", state)
            ctx.close()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        tell(ctx, StopChannel)
    }

    override fun channelRead(ctx: ChannelHandlerContext, message: Any) {
        if (message is ClientMessage) {
            tell(ctx, message)
        } else {
            logger.error("unsupported message:{}", message)
        }
    }

    private fun spawnChannelActor(ctx: ChannelHandlerContext): ActorRef<ChannelMessage> {
        val system = akkaSystem.system
        val resp: SpawnChannelActorResp = syncAsk(system, system.scheduler()) {
            SpawnChannelActorReq(ctx, it)
        }
        return resp.channelActorRef
    }
}
