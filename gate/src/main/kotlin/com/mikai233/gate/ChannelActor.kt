package com.mikai233.gate

import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.ShardingEnvelope
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.common.ext.shardingEnvelope
import com.mikai233.protocol.loginReq
import com.mikai233.shared.message.*
import io.netty.channel.ChannelHandlerContext

class ChannelActor(
    context: ActorContext<ChannelMessage>,
    private val handlerContext: ChannelHandlerContext,
    private val gateNode: GateNode,
) :
    AbstractBehavior<ChannelMessage>(context) {
    private val runnableAdapter = runnableAdapter { ChannelRunnable(it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()
    private val logger = actorLogger()
    private var playerId: Long = 0L
    private var birthWorldId: Long = 0L
    private var worldId: Long = 0L
    private val playerActor = gateNode.playerActor()

    init {
        logger.info("{} preStart", context.self)
        playerActor.tell(ShardingEnvelope("112233", PlayerLogin(context.self.narrow())))
        playerActor.tell(shardingEnvelope(112233.toString(), PlayerProtobufEnvelope(loginReq { id = 112233 })))
    }

    override fun createReceive(): Receive<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is ChannelRunnable -> {
                    message.run()
                }

                is ClientMessage -> TODO()
                is GracefulShutdown -> {
                    logger.info("{} {}", context.self, message)
                    return@onMessage Behaviors.stopped()
                }

                is Test -> {
                    logger.info("{}", message)
                }

                is ChannelProtobufEnvelope -> {
                    logger.info("{}", message)
                }
            }
            Behaviors.same()
        }.onSignal(PostStop::class.java) {
            logger.info("{}", it)
            Behaviors.same()
        }.build()
    }

    private fun tellPlayer(playerId: Long, message: SerdePlayerMessage) {
        playerActor.tell(shardingEnvelope("$playerId", message))
    }

    private fun tellWorld(message: ClientMessage) {

    }

    private fun write(message: GeneratedMessageV3) {
        handlerContext.writeAndFlush(message)
    }
}