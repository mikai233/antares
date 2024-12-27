package com.mikai233.player

import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.ExecuteActorFunction
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.Message
import com.mikai233.protocol.ProtoLogin
import com.mikai233.shared.message.*
import com.mikai233.shared.message.player.*
import kotlin.time.Duration.Companion.seconds

class PlayerActor(node: PlayerNode) : StatefulActor<PlayerNode>(node) {
    companion object {
        val TickDuration = 1.seconds

        fun props(node: PlayerNode): Props = Props.create(PlayerActor::class.java, node)
    }

    val playerId: Long = self.path().name().toLong()

    private var channelActor: ActorRef? = null
    val manager = PlayerDataManager(this, coroutineScope)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(HandoffPlayer::class.java) { context.stop(self) }
            .match(ActorNamedRunnable::class.java) { handleRunnable(it) }
            .matchAny {
                stash()
                context.become(initialize())
            }
            .build()
    }

    private fun initialize(): Receive {
        return receiveBuilder()
            .match(PlayerInitialized::class.java) {
                unstashAll()
                startTimerWithFixedDelay(PlayerTick, PlayerTick, TickDuration)
                context.become(active())
            }
            .match(ActorNamedRunnable::class.java) { handleRunnable(it) }
            .match(HandoffPlayer::class.java) { context.stop(self) }
            .matchAny { stash() }
            .build()
    }

    private fun active(): Receive {
        return receiveBuilder()
            .match(ActorNamedRunnable::class.java) { handleRunnable(it) }
            .match(HandoffPlayer::class.java) { context.become(stopping()) }
            .match(PlayerTick::class.java) {}
            .build()
    }

    private fun stopping(): Receive {
        return receiveBuilder()
            .match(ActorNamedRunnable::class.java) { handleRunnable(it) }
            .match(PlayerUnloaded::class.java) { context.stop(self) }
            .match(PlayerTick::class.java) {}
            .build()
    }

    private fun handleProtobufEnvelope(message: ProtobufEnvelope) {

    }

    private fun handlePlayerMessage(message: PlayerMessage) {
    }

    fun isOnline() = channelActor != null

    fun send(message: GeneratedMessage) {
        val channel = channelActor
        if (channel != null) {
            val envelope = ChannelProtobufEnvelope(message)
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

    private fun compilePlayerScript(message: PlayerScript) {
        node.scriptActor.tell(ExecuteActorScript(message.script), self)
    }

    private fun executeActorFunction(message: ExecuteActorFunction) {
        message.function.invoke(this)
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

    private fun handleRunnable(message: ActorNamedRunnable) {
        runCatching(message::run).onFailure {
            logger.error(it, "player:{} handle runnable:{} failed", playerId, message.name)
        }
    }

    fun execute(name: String, block: () -> Unit) {
        self tell ActorNamedRunnable(name, block)
    }
}
