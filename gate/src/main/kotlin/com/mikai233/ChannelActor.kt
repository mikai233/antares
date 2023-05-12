package com.mikai233

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Terminated
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.RunnableMessage
import com.mikai233.shared.message.SayHello
import com.mikai233.shared.message.SayWorld
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChannelActor(private val context: ActorContext<ChannelMessage>) : AbstractBehavior<ChannelMessage>(context) {
    private val runnableAdapter = context.messageAdapter(Runnable::class.java) { RunnableMessage(it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()

    private val logger = context.log
    private var count = 0

    init {
        logger.info("preStart")
        coroutine.launch {
            while (true) {
                context.self.tell(SayHello)
                delay(1000)
            }
        }
        repeat(100) {
            repeat(100) {
                coroutine.launch {
                    count++
                }
            }
        }

    }

    companion object {
        fun setup(): Behavior<ChannelMessage> {
            return Behaviors.setup(::ChannelActor)
        }
    }

    override fun createReceive(): Receive<ChannelMessage> {
        return newReceiveBuilder().onMessage(ChannelMessage::class.java) {
            when (it) {
                SayHello -> {
                    logger.info("hello:{}", count)
                }

                SayWorld -> {
                    logger.info("world")
                }

                is RunnableMessage -> {
                    it.run()
                }
            }
            Behaviors.same()
        }.onSignal(Terminated::class.java) {
            println("terminated")
            Behaviors.same()
        }.build()
    }
}

fun main() {
    val system = ActorSystem.create(ChannelActor.setup(), "test")
}