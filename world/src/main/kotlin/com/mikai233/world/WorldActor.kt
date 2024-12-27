package com.mikai233.world

import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.extension.*
import com.mikai233.shared.message.*
import com.mikai233.shared.message.world.HandoffWorld
import com.mikai233.shared.message.world.StopWorld
import com.mikai233.shared.message.world.WakeupWorld
import com.mikai233.shared.message.world.WorldProtobufEnvelope
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class WorldActor(node: WorldNode) : StatefulActor<WorldNode>(node) {
    companion object {
        val WorldTick = 1.seconds

        fun props(node: WorldNode): Props = Props.create(WorldActor::class.java, node)
    }

    val worldId: Long = self.path().name().toLong()

    val sessionManager = WorldSessionManager(this)
    val manager = WorldDataManager(this)

    override fun createReceive(): Receive {
        receiveBuilder().build()
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> {
                    executeWorldScript(message)
                }

                StopWorld -> {
                    return@onMessage Behaviors.stopped()
                }

                WakeupWorld -> {
                    manager.loadAll()
                }

                is ActorNamedRunnable -> {
                    handleWorldActorRunnable(message)
                }

                is BusinessWorldMessage -> {
                    buffer.stash(message)
                }

                WorldInitDone -> {
                    timers.startTimerAtFixedRate(WorldTick, WorldTick.toJavaDuration())
                    return@onMessage buffer.unstashAll(active())
                }

                WorldTick -> Unit
            }
            Behaviors.same()
        }.onSignal(PostStop::class.java) { message ->
            logger.info("worldId:{} {}", worldId, message)
            Behaviors.same()
        }.build()
    }

    private fun active(): Receive {
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> {
                    executeWorldScript(message)
                }

                StopWorld -> {
                    manager.stopAndFlush()
                    return@onMessage stopping()
                }

                is ActorNamedRunnable -> {
                    handleWorldActorRunnable(message)
                }

                WakeupWorld,
                WorldInitDone -> Unit

                is WorldProtobufEnvelope -> {
                    handleWorldProtobufEnvelope(message)
                }

                is BusinessWorldMessage -> {
                    handleBusinessWorldMessage(message)
                }

                WorldTick -> {
                    manager.tickDatabase()
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun stopping(): Behavior<WorldMessage> {
        return newReceiveBuilder().onMessage(WorldMessage::class.java) { message ->
            when (message) {
                is ExecuteWorldScript -> {
                    executeWorldScript(message)
                }

                StopWorld -> {
                    context.system.unsubscribe(context.self)
                    coroutine.cancelAll("StopWorld_$worldId")
                    return@onMessage Behaviors.stopped()
                }

                is ActorNamedRunnable -> {
                    handleWorldActorRunnable(message)
                }

                WakeupWorld,
                WorldInitDone,
                is BusinessWorldMessage -> Unit

                WorldTick -> {
                    if (manager.stopAndFlush()) {
                        context.self tell StopWorld
                    }
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun handleBusinessWorldMessage(message: BusinessWorldMessage) {
        internalDispatcher.dispatch(message::class, this, message)
    }

    private fun handleWorldProtobufEnvelope(message: WorldProtobufEnvelope) {
        val inner = message.message
        protobufDispatcher.dispatch(inner::class, this, inner)
    }

    fun passivate() {
        context.parent.tell(ShardRegion.Passivate(HandoffWorld), self)
    }

    private fun executeWorldScript(message: ExecuteWorldScript) {
        message.script.invoke(this)
    }

    fun tellPlayer(playerId: Long, message: SerdePlayerMessage) {
        playerActorSharding.tell(shardingEnvelope("$playerId", message))
    }

    fun tellWorld(worldId: Long, message: SerdeWorldMessage) {
        worldActorSharding.tell(shardingEnvelope("$worldId", message))
    }

    private fun handleWorldActorRunnable(message: ActorNamedRunnable): Behavior<WorldMessage> {
        runCatching(message::run).onFailure {
            logger.error("world actor handle runnable:{} failed", message.name, it)
        }
        return Behaviors.same()
    }

    fun submit(name: String, block: () -> Unit) {
        context.self tell ActorNamedRunnable(name, block)
    }
}
