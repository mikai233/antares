package com.mikai233

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ClientMessage
import com.mikai233.shared.message.GracefulShutdown
import com.mikai233.shared.message.RunnableMessage
import io.netty.channel.ChannelHandlerContext

class ChannelActor(context: ActorContext<ChannelMessage>, val handlerContext: ChannelHandlerContext) :
    AbstractBehavior<ChannelMessage>(context) {
    private val runnableAdapter = context.messageAdapter(Runnable::class.java) { RunnableMessage(it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()
    private val logger = actorLogger()
    private var playerId: Long = 0L
    private var birthWorldId: Long = 0L
    private var worldId: Long = 0L

    init {
        logger.info("{} preStart", context.self)
    }

    companion object {
        fun setup(handlerContext: ChannelHandlerContext): Behavior<ChannelMessage> {
            return Behaviors.setup {
                ChannelActor(it, handlerContext)
            }
        }
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
            }
            Behaviors.same()
        }.build()
    }

    private fun tellPlayer(message: ClientMessage) {

    }

    private fun tellWorld(message: ClientMessage) {

    }
}