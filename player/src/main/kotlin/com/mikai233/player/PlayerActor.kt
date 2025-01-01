package com.mikai233.player

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.cluster.sharding.ShardRegion
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Message
import com.mikai233.protocol.ProtoLogin
import com.mikai233.shared.message.*
import com.mikai233.shared.message.player.HandoffPlayer
import com.mikai233.shared.message.player.PlayerInitialized
import com.mikai233.shared.message.player.PlayerTick
import com.mikai233.shared.message.player.PlayerUnloaded
import kotlin.time.Duration.Companion.seconds

class PlayerActor(node: PlayerNode) : StatefulActor<PlayerNode>(node) {
    companion object {
        val PlayerTickDuration = 1.seconds

        fun props(node: PlayerNode): Props = Props.create(PlayerActor::class.java, node)
    }

    val playerId: Long = self.path().name().toLong()

    private var channelActor: ActorRef? = null
    val manager = PlayerDataManager(this)

    override fun preStart() {
        super.preStart()
        logger.info("PlayerActor[{}] started", self)
    }

    override fun postStop() {
        super.postStop()
        logger.info("PlayerActor[{}] stopped", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(HandoffPlayer::class.java) { context.stop(self) }
            .matchAny {
                context.become(initialize())
                manager.init()
                stash()
            }
            .build()
    }

    private fun initialize(): Receive {
        return receiveBuilder()
            .match(PlayerInitialized::class.java) {
                unstashAll()
                startTimerWithFixedDelay(PlayerTick, PlayerTick, PlayerTickDuration)
                context.become(active())
            }
            .match(HandoffPlayer::class.java) { context.stop(self) }
            .matchAny { stash() }
            .build()
    }

    private fun active(): Receive {
        return receiveBuilder()
            .match(HandoffPlayer::class.java) { context.become(stopping()) }
            .match(PlayerTick::class.java) { manager.tick() }
            .match(ProtobufEnvelope::class.java) { handleProtobufEnvelope(it) }
            .match(Terminated::class.java) { handleTerminated(it) }
            .match(Message::class.java) { handlePlayerMessage(it) }
            .build()
    }

    private fun handleTerminated(it: Terminated) {
        if (it.actor == channelActor) {
            logger.info("player:{} channel actor:{} terminated", playerId, channelActor)
            channelActor = null
        }
    }

    private fun stopping(): Receive {
        return receiveBuilder()
            .match(PlayerUnloaded::class.java) { context.stop(self) }
            .match(Terminated::class.java) { handleTerminated(it) }
            .match(PlayerTick::class.java) {
                if (manager.flush()) {
                    self tell PlayerUnloaded
                }
            }
            .build()
    }

    private fun handleProtobufEnvelope(envelope: ProtobufEnvelope) {
        val message = envelope.message
        try {
            node.protobufDispatcher.dispatch(message::class, this, message)
        } catch (e: Exception) {
            logger.error(e, "player:{} handle protobuf message:{} failed", playerId, message)
        }
    }

    private fun handlePlayerMessage(message: Message) {
        try {
            node.internalDispatcher.dispatch(message::class, this, message)
        } catch (e: Exception) {
            logger.error(e, "player:{} handle message:{} failed", playerId, message)
        }
    }

    fun isOnline() = channelActor != null

    fun send(message: GeneratedMessage) {
        val channel = channelActor
        if (channel != null) {
            val envelope = ServerProtobuf(message)
            channel tell envelope
        } else {
            logger.warning("player:{} unable to send message to channel actor, because channel actor is null", playerId)
        }
    }

    fun passivate() {
        context.parent.tell(ShardRegion.Passivate(HandoffPlayer), self)
    }

    fun bindChannelActor(incomingChannelActor: ActorRef) {
        if (incomingChannelActor != channelActor) {
            channelActor?.let {
                context.unwatch(it)
                logger.info("player:{} unbind old channel actor:{}", playerId, it)
                it tell ChannelExpired(ProtoLogin.ConnectionExpiredNotify.Reason.MultiLogin_VALUE)
            }
            channelActor = incomingChannelActor
            context.watch(channelActor)
            logger.info("player:{} bind new channel actor:{}", playerId, channelActor)
        }
    }

    fun tellPlayer(message: PlayerMessage) {
        node.playerSharding.tell(message, self)
    }

    fun forwardPlayer(message: PlayerMessage) {
        node.playerSharding.forward(message, context)
    }

    suspend fun <R> askPlayer(message: PlayerMessage): Result<R> where  R : Message {
        return node.playerSharding.ask(message)
    }

    fun tellWorld(message: WorldMessage) {
        node.worldSharding.tell(message, self)
    }

    fun forwardWorld(message: WorldMessage) {
        node.worldSharding.forward(message, context)
    }

    suspend fun <R> askWorld(message: WorldMessage): Result<R> where R : Message {
        return node.worldSharding.ask(message)
    }
}
