package com.mikai233.player

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Terminated
import akka.actor.typed.javadsl.*
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.player.component.PlayerActorDispatchers
import com.mikai233.player.component.PlayerScriptSupport
import com.mikai233.shared.message.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class PlayerActor(
    context: ActorContext<PlayerMessage>,
    private val buffer: StashBuffer<PlayerMessage>,
    val timers: TimerScheduler<PlayerMessage>,
    val playerId: Long,
    val playerNode: PlayerNode
) :
    AbstractBehavior<PlayerMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { PlayerRunnable(it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private var channelActor: ActorRef<SerdeChannelMessage>? = null
    private val protobufDispatcher = playerNode.server.component<PlayerActorDispatchers>().protobufDispatcher
    private val internalDispatcher = playerNode.server.component<PlayerActorDispatchers>().internalDispatcher
    private val localScriptActor = playerNode.server.component<PlayerScriptSupport>().localScriptActor

    init {
        logger.info("{} preStart", playerId)
        coroutine.launch(Dispatchers.IO) {
            logger.info("pretend load data")
            delay(2000)
            context.self.tell(PlayerInitDone)
        }
    }

    override fun createReceive(): Receive<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                StopPlayer -> {
                    return@onMessage Behaviors.stopped()
                }

                PlayerInitDone -> {
                    return@onMessage buffer.unstashAll(active())
                }

                is PlayerRunnable -> {
                    message.run()
                }

                is BusinessPlayerMessage -> {
                    buffer.stash(message)
                }

                is ExecutePlayerScript -> {
                    executePlayerScript(message)
                }

                is PlayerScript -> {
                    compilePlayerScript(message)
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun active(): Behavior<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is PlayerProtobufEnvelope -> {
                    handlePlayerProtobufEnvelope(message)
                }

                is PlayerRunnable -> {
                    message.run()
                }

                PlayerInitDone -> Unit
                StopPlayer -> {
                    coroutine.cancelAll("StopPlayer")
                    coroutine.launch {
                        logger.info("pretend stopping operation")
                        delay(2000)
                        context.self.tell(StopPlayer)
                    }
                    return@onMessage stopping()
                }

                is ExecutePlayerScript -> {
                    message.script.invoke(this)
                }

                is PlayerScript -> {
                    compilePlayerScript(message)
                }

                is BusinessPlayerMessage -> handleBusinessPlayerMessage(message)
            }
            Behaviors.same()
        }.onSignal(Terminated::class.java) { terminated ->
            val who = terminated.ref
            if (who == channelActor) {
                channelActor = null
                logger.info("player:{} channel actor:{} terminated", playerId, who)
            }
            Behaviors.same()
        }.build()
    }

    private fun handlePlayerProtobufEnvelope(message: PlayerProtobufEnvelope) {
        val inner = message.inner
        protobufDispatcher.dispatch(inner::class, this, inner)
    }

    private fun handleBusinessPlayerMessage(message: BusinessPlayerMessage) {
        internalDispatcher.dispatch(message::class, this, message)
    }

    private fun stopping(): Behavior<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is PlayerRunnable -> {
                    message.run()
                }

                PlayerInitDone,
                is BusinessPlayerMessage -> Unit

                StopPlayer -> return@onMessage Behaviors.stopped()
                is ExecutePlayerScript -> {
                    executePlayerScript(message)
                }

                is PlayerScript -> {
                    compilePlayerScript(message)
                }
            }
            Behaviors.same()
        }.onSignal(PostStop::class.java) { message ->
            logger.info("player:{} {}", playerId, message)
            Behaviors.same()
        }.build()
    }

    fun write(message: GeneratedMessageV3) {
        val channel = channelActor
        if (channel != null) {
            val envelope = ChannelProtobufEnvelope(message)
            channel.tell(envelope)
        } else {
            logger.warn("player:{} unable to write message to channel actor, because channel actor is null", playerId)
        }
    }

    fun stop() {
        context.self.tell(StopPlayer)
    }

    private fun bindChannelActor(incomingChannelActor: ActorRef<SerdeChannelMessage>) {
        if (incomingChannelActor != channelActor) {
            channelActor?.let {
                context.unwatch(it)
                logger.info("player:{} unbind old channel actor:{}", playerId, it)
            }
            channelActor = incomingChannelActor
            context.watch(channelActor)
            logger.info("player:{} bind new channel actor:{}", playerId, channelActor)
        }
    }

    private fun compilePlayerScript(message: PlayerScript) {
        localScriptActor.tell(CompilePlayerActorScript(message.script, context.self))
    }

    private fun executePlayerScript(message: ExecutePlayerScript) {
        message.script.invoke(this)
    }
}