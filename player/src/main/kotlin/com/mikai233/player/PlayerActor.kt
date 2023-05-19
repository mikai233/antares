package com.mikai233.player

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.player.component.MessageDispatchers
import com.mikai233.player.component.ScriptSupport
import com.mikai233.shared.message.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class PlayerActor(
    context: ActorContext<PlayerMessage>,
    private val buffer: StashBuffer<PlayerMessage>,
    val playerId: Long,
    val playerNode: PlayerNode
) :
    AbstractBehavior<PlayerMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { PlayerRunnable(it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private lateinit var channelActorRef: ActorRef<SerdeChannelMessage>
    private val protobufDispatcher = playerNode.server.component<MessageDispatchers>().protobufDispatcher
    private val internalDispatcher = playerNode.server.component<MessageDispatchers>().internalDispatcher
    private val localScriptActor = playerNode.server.component<ScriptSupport>().localScriptActor
    lateinit var timerScheduler: TimerScheduler<PlayerMessage>
        private set

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

                is PlayerRunnable -> message.run()
                is PlayerLogin,
                is PlayerProtobufEnvelope -> {
                    buffer.stash(message)
                }

                is ExecutePlayerScript -> {
                    message.script.invoke(this)
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun active(): Behavior<PlayerMessage> {
        return Behaviors.withTimers { timers ->
            timerScheduler = timers
            newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
                when (message) {
                    is PlayerProtobufEnvelope -> {
                        val protoMessage = message.message
                        protobufDispatcher.dispatch(protoMessage::class, this, protoMessage)
                    }

                    is PlayerRunnable -> {
                        message.run()
                    }

                    is PlayerLogin -> {
                        logger.info("{}", message)
                        channelActorRef = message.channelActor
                        channelActorRef.tell(Test("hello"))
                    }

                    PlayerInitDone -> return@onMessage Behaviors.unhandled()
                    StopPlayer -> {
                        coroutine.cancelAll("StopPlayer")
                        coroutine.launch {
                            logger.info("pretend stopping operation")
                            delay(2000)
                            context.self.tell(StopPlayer)
                        }
                        return@onMessage stopping()
                    }

                    is ExecutePlayerScript -> TODO()
                }
                Behaviors.same()
            }.build()
        }
    }

    private fun stopping(): Behavior<PlayerMessage> {
        return newReceiveBuilder().onMessage(PlayerMessage::class.java) { message ->
            when (message) {
                is PlayerRunnable -> {
                    message.run()
                }

                PlayerInitDone,
                is PlayerLogin,
                is PlayerProtobufEnvelope -> return@onMessage Behaviors.unhandled()

                StopPlayer -> return@onMessage Behaviors.stopped()
                is ExecutePlayerScript -> {
                    message.script.invoke(this)
                }
            }
            Behaviors.same()
        }.build()
    }
}