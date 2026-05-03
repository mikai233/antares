package com.mikai233.world

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Message
import com.mikai233.common.message.world.*
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.idForServerMessage
import io.github.realmlabs.asteria.actor.ActorTimerSupport
import io.github.realmlabs.asteria.script.pekko.ScriptableAsteriaActor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.Props
import org.apache.pekko.cluster.sharding.ShardRegion
import kotlin.time.Duration.Companion.seconds

class WorldActor(val node: WorldNode) : ScriptableAsteriaActor<WorldNode>(node) {
    companion object {
        val WorldTickDuration = 1.seconds

        fun props(node: WorldNode): Props = Props.create(WorldActor::class.java, node)
    }

    val worldId: Long = self.path().name().toLong()

    private val timers = ActorTimerSupport(this)
    val sessionManager = WorldSessionManager(this)
    val manager = WorldDataManager(this)

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
                timers.startTimerWithFixedDelay(WorldTick, WorldTick, WorldTickDuration)
                self tell WorldActiveEvent
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
            .match(GeneratedMessage::class.java) { handleProtobufMessage(it) }
            .match(Message::class.java) { handleWorldMessage(it) }
            .build()
    }

    private fun stopping(): Receive {
        return receiveBuilder()
            .match(WorldUnloaded::class.java) { context.stop(self) }
            .match(WorldTick::class.java) {
                manager.flush { flushed ->
                    if (flushed) {
                        self tell WorldUnloaded
                    }
                }
            }
            .build()
    }

    private fun handleWorldMessage(message: Message) {
        try {
            node.internalDispatcher.dispatch(this, message)
        } catch (e: Exception) {
            logger.error(e, "world:{} handle message:{} failed", worldId, message)
        }
    }

    private fun handleProtobufMessage(message: GeneratedMessage) {
        if (message is GmReq && sessionManager[message.playerId] == null) {
            logger.warning("Session[{}] not found", message.playerId)
            return
        }
        try {
            node.protobufDispatcher.dispatch(this, message)
        } catch (e: Exception) {
            logger.error(e, "world:{} handle protobuf message:{} failed", worldId, message)
        }
    }

    fun passivate() {
        context.parent.tell(ShardRegion.Passivate(HandoffWorld), self)
    }

    fun tellPlayer(message: GeneratedMessage, sender: ActorRef = self) {
        node.playerSharding.tell(message, sender)
    }

    fun forwardPlayer(message: GeneratedMessage) {
        node.playerSharding.forward(message, context)
    }

    suspend fun <R> askPlayer(message: GeneratedMessage): Result<R> {
        return node.playerSharding.ask(message)
    }

    fun tellWorld(message: GeneratedMessage, sender: ActorRef = self) {
        node.worldSharding.tell(message, sender)
    }

    fun forwardWorld(message: GeneratedMessage) {
        node.worldSharding.forward(message, context)
    }

    suspend fun <R> askWorld(message: GeneratedMessage): Result<R> {
        return node.worldSharding.ask(message)
    }

    fun nextId() = node.idGenerator.nextId()

    private fun canInitialize(): Boolean {
        return worldId in node.gameWorldIds
    }

    fun broadcast(message: GeneratedMessage, topic: String, include: Set<Long>, exclude: Set<Long>) {
        node.broadcastRouter.tell(
            PlayerBroadcastEnvelope.newBuilder()
                .setTopic(topic)
                .addAllInclude(include)
                .addAllExclude(exclude)
                .setMessageId(idForServerMessage(message.javaClass))
                .setPayload(message.toByteString())
                .build(),
        )
    }
}
