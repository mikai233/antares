package com.mikai233.world

import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.extension.ask
import com.mikai233.common.message.ExecuteActorFunction
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

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(HandoffWorld::class.java) { context.stop(self) }
            .matchAny {
                stash()
                context.become(initialize())
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
            .match(WorldTick::class.java) {}
            .match(ProtobufEnvelope::class.java) { handleProtobufEnvelope(it) }
            .match(WorldMessage::class.java) { handleWorldMessage(it) }
            .build()
    }

    private fun stopping(): Receive {
        return receiveBuilder()
            .match(WorldUnloaded::class.java) { context.stop(self) }
            .match(WorldTick::class.java) {}
            .build()
    }

    private fun handleWorldMessage(message: WorldMessage) {
        node.internalDispatcher.dispatch(message::class, this, message)
    }

    private fun handleProtobufEnvelope(envelope: ProtobufEnvelope) {
        val message = envelope.message
        node.protobufDispatcher.dispatch(message::class, this, message)
    }

    fun passivate() {
        context.parent.tell(ShardRegion.Passivate(HandoffWorld), self)
    }

    private fun executeWorldScript(message: ExecuteActorFunction) {
        message.function.invoke(this)
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
}
