package com.mikai233.player

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.player.*
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoRpc.ChannelExpiredReq
import com.mikai233.protocol.ProtoRpc.PlayerChannelClosedReq
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import io.github.mikai233.asteria.actor.ActorTimerSupport
import io.github.mikai233.asteria.script.pekko.ScriptableAsteriaActor
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.ReceiveTimeout
import org.apache.pekko.cluster.sharding.ShardRegion
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class PlayerActor(val node: PlayerNode) : ScriptableAsteriaActor<PlayerNode>(node) {
    companion object {
        val PlayerTickDuration = 1.seconds

        fun props(node: PlayerNode): Props = Props.create(PlayerActor::class.java, node)
    }

    val playerId: Long = self.path().name().toLong()

    private var channelActorPath: String? = null
    private val timers = ActorTimerSupport(this)
    val manager = PlayerDataManager(this)

    override fun preStart() {
        super.preStart()
        timers.start()
        node.system.eventStream.subscribe(self, GameConfigUpdateEvent::class.java)
        logger.info("{} started", self)
    }

    override fun postStop() {
        super.postStop()
        logger.info("{} stopped", self)
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
                timers.startTimerWithFixedDelay(PlayerTick, PlayerTick, PlayerTickDuration)
                context.setReceiveTimeout(1.minutes.toJavaDuration())
                context.become(active())
            }
            .match(HandoffPlayer::class.java) { context.stop(self) }
            .matchAny { stash() }
            .build()
    }

    private fun active(): Receive {
        return receiveBuilder()
            .match(HandoffPlayer::class.java) {
                context.cancelReceiveTimeout()
                context.become(stopping())
            }
            .match(PlayerTick::class.java) { manager.tick() }
            .match(GeneratedMessage::class.java) { handleProtobufMessage(it) }
            .match(ReceiveTimeout::class.java) { if (!isOnline()) passivate() }
            .match(GameConfigUpdateEvent::class.java) { handlePlayerMessage(it) }
            .match(PlayerLoginEvent::class.java) { handlePlayerMessage(it) }
            .match(PlayerCreateEvent::class.java) { handlePlayerMessage(it) }
            .match(GameConfigUpdatedEvent::class.java) { handlePlayerMessage(it) }
            .build()
    }

    private fun stopping(): Receive {
        return receiveBuilder()
            .match(PlayerUnloaded::class.java) { context.stop(self) }
            .match(PlayerTick::class.java) {
                manager.flush { flushed ->
                    if (flushed) {
                        self tell PlayerUnloaded
                    }
                }
            }
            .build()
    }

    private fun handleProtobufMessage(message: GeneratedMessage) {
        try {
            node.protobufDispatcher.dispatch(this, message)
        } catch (e: Exception) {
            logger.error(e, "player:{} handle protobuf message:{} failed", playerId, message)
        }
    }

    private fun handlePlayerMessage(message: Any) {
        try {
            node.internalDispatcher.dispatch(this, message)
        } catch (e: Exception) {
            logger.error(e, "player:{} handle message:{} failed", playerId, message)
        }
    }

    fun isOnline() = channelActorPath != null

    fun send(message: GeneratedMessage) {
        val path = channelActorPath
        if (path != null) {
            context.actorSelection(path).tell(message, self)
        } else {
            logger.warning("player:{} unable to send message to channel actor, because channel actor is null", playerId)
        }
    }

    fun passivate() {
        context.parent.tell(ShardRegion.Passivate(HandoffPlayer), self)
    }

    fun bindChannelActorPath(incomingChannelActorPath: String) {
        if (incomingChannelActorPath != channelActorPath) {
            channelActorPath?.let {
                logger.info("player:{} unbind old channel actor path:{}", playerId, it)
                context.actorSelection(it).tell(
                    ChannelExpiredReq.newBuilder()
                        .setReason(ProtoLogin.ConnectionExpiredNotify.Reason.MultiLogin_VALUE)
                        .build(),
                    self,
                )
            }
            channelActorPath = incomingChannelActorPath
            logger.info("player:{} bind new channel actor path:{}", playerId, channelActorPath)
        }
    }

    fun clearChannelActorPath() {
        channelActorPath = null
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
