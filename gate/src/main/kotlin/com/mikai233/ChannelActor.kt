package com.mikai233

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.shared.message.*
import io.netty.channel.ChannelHandlerContext

class ChannelActor(
    context: ActorContext<ChannelMessage>,
    private val handlerContext: ChannelHandlerContext,
    private val player: ActorRef<PlayerMessage>
) :
    AbstractBehavior<ChannelMessage>(context) {
    private val runnableAdapter = context.messageAdapter(Runnable::class.java) { RunnableMessage(it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()
    private val logger = actorLogger()
    private var playerId: Long = 0L
    private var birthWorldId: Long = 0L
    private var worldId: Long = 0L

    init {
        logger.info("{} preStart", context.self)
        player.tell(PlayerLogin(112233, context.self.narrow()))
    }

    override fun createReceive(): Receive<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) { message ->
            when (message) {
                is RunnableMessage -> {
                    message.run()
                }

                is ClientMessage -> TODO()
                is GracefulShutdown -> {
                    logger.debug("{} {}", context.self, message)
                    return@onMessage Behaviors.stopped()
                }

                is Test -> {
                    logger.info("{}", message)
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun tellPlayer(message: InternalPlayerMessage) {
        player.tell(message)
    }

    private fun tellWorld(message: ClientMessage) {

    }

    private fun write(message: GeneratedMessageV3) {
        handlerContext.writeAndFlush(message)
    }
}