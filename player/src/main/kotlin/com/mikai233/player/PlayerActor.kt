package com.mikai233.player

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.shared.message.*

class PlayerActor(context: ActorContext<PlayerMessage>, private val playerId: Long) :
    AbstractBehavior<PlayerMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter =
        context.messageAdapter(Runnable::class.java) { PlayerRunnableMessage(playerId, it::run) }
    private val coroutine = runnableAdapter.safeActorCoroutine()
    private lateinit var channelActorRef: ActorRef<InternalChannelMessage>

    init {
        logger.info("{} preStart", playerId)
    }

    override fun createReceive(): Receive<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is ClientToPlayerMessage -> TODO()
                is PlayerRunnableMessage -> TODO()
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
        }.build()
    }
}