package com.mikai233.world

import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Message
import com.mikai233.shared.message.PlayerMessage
import com.mikai233.shared.message.ProtobufEnvelope
import com.mikai233.shared.message.WorldMessage
import com.mikai233.shared.message.world.HandoffWorld
import com.mikai233.shared.message.world.WorldInitialized
import com.mikai233.shared.message.world.WorldTick
import com.mikai233.shared.message.world.WorldUnloaded
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
        logger.info("WorldActor[{}] started", self)
    }

    override fun postStop() {
        clearStash()
        logger.info("WorldActor[{}] stopped", self)
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
            .match(ProtobufEnvelope::class.java) { handleProtobufEnvelope(it) }
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
            node.internalDispatcher.dispatch(message::class, this, message)
        } catch (e: Exception) {
            logger.error(e, "world:{} handle message:{} failed", worldId, message)
        }
    }

    private fun handleProtobufEnvelope(envelope: ProtobufEnvelope) {
        val message = envelope.message
        try {
            node.protobufDispatcher.dispatch(message::class, this, message)
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
}
