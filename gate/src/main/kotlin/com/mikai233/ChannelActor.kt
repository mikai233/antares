package com.mikai233

import akka.actor.typed.Behavior
import akka.actor.typed.Terminated
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
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) {
            when (it) {
                is RunnableMessage -> {
                    it.run()
                }

                is ClientMessage -> TODO()
                is GracefulShutdown -> TODO()
            }
            Behaviors.same()
        }.onSignal(Terminated::class.java) {
            println("terminated")
            Behaviors.same()
        }.build()
    }
}