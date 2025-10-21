package com.mikai233.world

import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Message
import com.mikai233.common.message.WorldProtobufEnvelope
import com.mikai233.common.message.player.PlayerMessage
import com.mikai233.common.message.world.*
import kotlin.time.Duration.Companion.seconds

class WorldActor(node: WorldNode) : StatefulActor<WorldNode>(node) {
    companion object {
        val WorldTickDuration = 1.seconds

        fun props(node: WorldNode): Props = Props.create(WorldActor::class.java, node)
    }

    val worldId: Long = self.path().name().toLong()

    val sessionManager = WorldSessionManager(this)
    val manager = WorldDataManager(this)

    override fun preStart() {
        super.preStart()
        node.system.eventStream.subscribe(self, GameConfigUpdateEvent::class.java)
        logger.info("{} started", self)
    }

    override fun postStop() {
        super.postStop()
        logger.info("{} stopped", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(HandoffWorld::class.java) { context.stop(self) }
            .matchAny {
                if (canInitialize()) {
                    context.become(initialize())
                    stash()
                    manager.init()
                } else {
                    context.stop(self)
                    logger.error("WorldActor[{}] could not initialize", worldId)
                }
            }
            .build()
    }

    private fun initialize(): Receive {
        return receiveBuilder()
            .match(WorldInitialized::class.java) {
                unstashAll()
                startTimerWithFixedDelay(WorldTick, WorldTick, WorldTickDuration)
                fireEvent(WorldActiveEvent)
                context.become(active())
            }
            .match(HandoffWorld::class.java) { context.stop(self) }
            .matchAny { stash() }
            .build()
    }

    private fun active(): Receive {
        return receiveBuilder()
            .match(HandoffWorld::class.java) { context.become(stopping()) }
            .match(WorldTick::class.java) { manager.tick() }
            .match(WorldProtobufEnvelope::class.java) { handleProtobufEnvelope(it) }
            .match(Message::class.java) { handleWorldMessage(it) }
            .build()
    }

    private fun stopping(): Receive {
        return receiveBuilder()
            .match(WorldUnloaded::class.java) { context.stop(self) }
            .match(WorldTick::class.java) {
                if (manager.flush()) {
                    self tell WorldUnloaded
                }
            }
            .build()
    }

    private fun handleWorldMessage(message: Message) {
        try {
            node.internalDispatcher.dispatch(message::class, message, this)
        } catch (e: Exception) {
            logger.error(e, "world:{} handle message:{} failed", worldId, message)
        }
    }

    private fun handleProtobufEnvelope(envelope: WorldProtobufEnvelope) {
        val message = envelope.message
        val session = sessionManager[envelope.playerId]
        if (session == null) {
            logger.warning("Session[{}] not found", envelope.playerId)
            return
        }
        try {
            node.protobufDispatcher.dispatch(message::class, message, this, session)
        } catch (e: Exception) {
            logger.error(e, "world:{} handle protobuf message:{} failed", worldId, message)
        }
    }

    fun passivate() {
        context.parent.tell(ShardRegion.Passivate(HandoffWorld), self)
    }

    fun tellPlayer(message: PlayerMessage, sender: ActorRef = self) {
        node.playerSharding.tell(message, sender)
    }

    fun forwardPlayer(message: PlayerMessage) {
        node.playerSharding.forward(message, context)
    }

    suspend fun <R> askPlayer(message: PlayerMessage): Result<R> where R : Message {
        return node.playerSharding.ask(message)
    }

    fun tellWorld(message: WorldMessage, sender: ActorRef = self) {
        node.worldSharding.tell(message, sender)
    }

    fun forwardWorld(message: WorldMessage) {
        node.worldSharding.forward(message, context)
    }

    suspend fun <R> askWorld(message: WorldMessage): Result<R> where R : Message {
        return node.worldSharding.ask(message)
    }

    fun nextId() = node.snowflakeGenerator.nextId()

    private fun canInitialize(): Boolean {
        return node.gameWorldMeta.worlds.contains(worldId)
    }

    fun broadcast(message: GeneratedMessage, topic: String, include: Set<Long>, exclude: Set<Long>) {
        node.broadcastRouter.tell(PlayerBroadcastEnvelope(topic, include, exclude, message))
    }
}
