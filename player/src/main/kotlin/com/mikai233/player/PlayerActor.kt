package com.mikai233.player

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.event.GameConfigChangedEvent
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.ask
import com.mikai233.common.message.Message
import com.mikai233.common.runtime.system
import com.mikai233.player.message.HandoffPlayer
import com.mikai233.player.message.PlayerTick
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoRpcGate.ChannelExpiredReq
import com.mikai233.protocol.ProtoRpcPlayer.PlayerShutdownAck
import io.github.realmlabs.asteria.actor.ActorLifecycleGate
import io.github.realmlabs.asteria.actor.ActorTimerSupport
import io.github.realmlabs.asteria.actor.AsteriaActor
import io.github.realmlabs.asteria.message.dispatchActor
import io.github.realmlabs.asteria.script.pekko.ActorScriptSupport
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.ReceiveTimeout
import org.apache.pekko.cluster.sharding.ShardRegion
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class PlayerActor(val node: PlayerNode) : AsteriaActor<PlayerNode>(node) {
    companion object {
        val PlayerTickDuration = 1.seconds

        fun props(node: PlayerNode): Props = Props.create(PlayerActor::class.java, node)
    }

    val playerId: Long = self.path().name().toLong()

    private var channelActor: ActorRef? = null
    private var shutdownStarted = false
    private val timers = ActorTimerSupport(this)
    private val scripts = ActorScriptSupport(this)
    val manager = PlayerDataManager(this)
    private val lifecycle = ActorLifecycleGate(
        owner = this,
        load = { manager.load() },
        flush = { manager.flush() },
    )

    override fun preStart() {
        super.preStart()
        timers.start()
        node.system.eventStream.subscribe(self, GameConfigChangedEvent::class.java)
        lifecycle.startLoading()
        logger.info("{} started", self)
    }

    override fun postStop() {
        super.postStop()
        logger.info("{} stopped", self)
    }

    override fun createReceive(): Receive {
        return lifecycle.loadingReceive { withScripts(running()) }
    }

    private fun running(): Receive {
        timers.startTimerWithFixedDelay(PlayerTick, PlayerTick, PlayerTickDuration)
        context.setReceiveTimeout(1.minutes.toJavaDuration())
        return active()
    }

    private fun active(): Receive {
        return receiveBuilder()
            .match(HandoffPlayer::class.java) {
                context.cancelReceiveTimeout()
                lifecycle.beginStop()
            }
            .match(PlayerTick::class.java) { manager.tick() }
            .match(GeneratedMessage::class.java) { handleProtobufMessage(it) }
            .match(ReceiveTimeout::class.java) { if (!isOnline()) passivate() }
            .match(GameConfigChangedEvent::class.java) { handlePlayerMessage(it) }
            .match(PlayerLoginEvent::class.java) { handlePlayerMessage(it) }
            .match(PlayerCreateEvent::class.java) { handlePlayerMessage(it) }
            .build()
    }

    private fun withScripts(receive: Receive): Receive {
        return receive.orElse(scripts.receive())
    }

    private fun handleProtobufMessage(message: GeneratedMessage) {
        try {
            node.protobufDispatcher.dispatchActor(node, this, message)
        } catch (e: Exception) {
            logger.error(e, "player:{} handle protobuf message:{} failed", playerId, message)
        }
    }

    private fun handlePlayerMessage(message: Message) {
        try {
            node.internalDispatcher.dispatchActor(node, this, message)
        } catch (e: Exception) {
            logger.error(e, "player:{} handle message:{} failed", playerId, message)
        }
    }

    fun isOnline() = channelActor != null

    fun send(message: GeneratedMessage) {
        val boundChannelActor = channelActor
        if (boundChannelActor != null) {
            boundChannelActor.tell(message, self)
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
                logger.info("player:{} unbind old channel actor:{}", playerId, it)
                it.tell(
                    ChannelExpiredReq.newBuilder()
                        .setReason(ProtoLogin.ConnectionExpiredNotify.Reason.MultiLogin_VALUE)
                        .build(),
                    self,
                )
            }
            channelActor = incomingChannelActor
            logger.info("player:{} bind new channel actor:{}", playerId, channelActor)
        }
    }

    fun clearChannelActor() {
        channelActor = null
    }

    fun shutdownForPlan(planId: String, coordinator: ActorRef) {
        if (shutdownStarted) {
            return
        }
        shutdownStarted = true
        channelActor = null
        context.cancelReceiveTimeout()
        context.become(receiveBuilder().build())
        launch(timeout = null) {
            val result = runCatching { manager.flush() }
            val ack = PlayerShutdownAck.newBuilder()
                .setPlayerId(playerId)
                .setShutdownPlanId(planId)
                .setSuccess(result.getOrDefault(false))
                .also { builder ->
                    result.exceptionOrNull()?.localizedMessage?.let(builder::setError)
                }
                .build()
            coordinator.tell(ack, self)
            context.stop(self)
        }
    }

    fun tellPlayer(message: GeneratedMessage) {
        node.playerSharding.tell(message, self)
    }

    fun forwardPlayer(message: GeneratedMessage) {
        node.playerSharding.forward(message, context)
    }

    suspend fun <R> askPlayer(message: GeneratedMessage): Result<R> {
        return node.playerSharding.ask(message)
    }

    fun tellWorld(message: GeneratedMessage) {
        node.worldSharding.tell(message, self)
    }

    fun forwardWorld(message: GeneratedMessage) {
        node.worldSharding.forward(message, context)
    }

    suspend fun <R> askWorld(message: GeneratedMessage): Result<R> {
        return node.worldSharding.ask(message)
    }

    fun nextId() = node.idGenerator.nextId()
}
