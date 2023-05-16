package com.mikai233.player

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.shared.message.*

class PlayerActor(context: ActorContext<PlayerMessage>, private val playerId: Long, val playerNode: PlayerNode) :
    AbstractBehavior<PlayerMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { PlayerRunnable(it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()
    private lateinit var channelActorRef: ActorRef<SerdeChannelMessage>

    init {
        logger.info("{} preStart", playerId)
    }

    override fun createReceive(): Receive<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is PlayerProtobufEnvelope -> {
                    logger.info("{}", message)
                }

                is PlayerRunnable -> TODO()
                is PlayerLogin -> {
                    logger.info("{}", message)
                    channelActorRef = message.channelActor
                    channelActorRef.tell(Test("hello"))
                }

                StopPlayer -> {
                    return@onMessage Behaviors.stopped()
                }
            }
            Behaviors.same()
        }.onAnyMessage {
            logger.info("{}", it)
            Behaviors.same()
        }.build()
    }
}