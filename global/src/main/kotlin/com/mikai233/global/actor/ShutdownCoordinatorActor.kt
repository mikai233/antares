package com.mikai233.global.actor

import com.mikai233.common.extension.encodeActorRef
import com.mikai233.common.runtime.GameRoles
import com.mikai233.common.runtime.gameWorldIds
import com.mikai233.common.runtime.system
import com.mikai233.common.shutdown.GATE_DRAIN_TOPIC
import com.mikai233.global.GlobalNode
import com.mikai233.global.message.HandoffShutdownCoordinator
import com.mikai233.protocol.ProtoRpcPlayer.PlayerShutdownAck
import com.mikai233.protocol.ProtoRpcShutdown.*
import com.mikai233.protocol.ProtoRpcWorld.WorldShutdownAck
import com.mikai233.protocol.ProtoRpcWorld.WorldShutdownReq
import io.github.realmlabs.asteria.actor.AsteriaActor
import org.apache.pekko.actor.Props
import org.apache.pekko.cluster.Cluster
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.pubsub.DistributedPubSub
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.Publish
import java.time.Duration

class ShutdownCoordinatorActor(val node: GlobalNode) : AsteriaActor<GlobalNode>(node) {
    companion object {
        fun props(node: GlobalNode): Props = Props.create(ShutdownCoordinatorActor::class.java, node)
    }

    private val mediator = DistributedPubSub.get(context.system).mediator()
    private val gateDrainTimeout = node.config.getDurationOrDefault(
        "game.shutdown.timeout.gate-drain",
        Duration.ofSeconds(30),
    )
    private val playerDrainTimeout = node.config.getDurationOrDefault(
        "game.shutdown.timeout.player-drain",
        Duration.ofMinutes(2),
    )
    private val worldStopTimeout = node.config.getDurationOrDefault(
        "game.shutdown.timeout.world-stop",
        Duration.ofMinutes(3),
    )

    private var planId: String? = null
    private var requestedBy: String? = null
    private var phase: ShutdownPhase = ShutdownPhase.SHUTDOWN_PHASE_IDLE
    private var generation: Long = 0
    private var expectedGateCount: Int = 0
    private val drainedGateNodes = linkedSetOf<String>()
    private val expectedPlayerIds = linkedSetOf<Long>()
    private val flushedPlayerIds = linkedSetOf<Long>()
    private val expectedWorldIds = linkedSetOf<Long>()
    private val flushedWorldIds = linkedSetOf<Long>()
    private val errors = mutableListOf<String>()

    override fun preStart() {
        super.preStart()
        logger.info("{} started", self)
    }

    override fun postStop() {
        super.postStop()
        logger.info("{} stopped", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ShutdownStartReq::class.java) { handleStart(it) }
            .match(ShutdownStatusReq::class.java) { sender.tell(status(), self) }
            .match(GateDrainAck::class.java) { handleGateDrainAck(it) }
            .match(PlayerShutdownAck::class.java) { handlePlayerShutdownAck(it) }
            .match(WorldShutdownAck::class.java) { handleWorldShutdownAck(it) }
            .match(ShutdownPhaseTimeout::class.java) { handlePhaseTimeout(it) }
            .match(HandoffShutdownCoordinator::class.java) { context.stop(self) }
            .build()
    }

    private fun handleStart(command: ShutdownStartReq) {
        if (
            phase !in setOf(
                ShutdownPhase.SHUTDOWN_PHASE_IDLE,
                ShutdownPhase.SHUTDOWN_PHASE_COMPLETED,
                ShutdownPhase.SHUTDOWN_PHASE_FAILED,
            )
        ) {
            sender.tell(status(), self)
            return
        }
        reset(command)
        enterPhase(ShutdownPhase.SHUTDOWN_PHASE_DRAINING_GATES)
        expectedGateCount = activeRoleMemberCount(GameRoles.Gate)
        logger.info(
            "shutdown plan started planId={} requestedBy={} expectedGateCount={}",
            command.planId,
            command.requestedBy,
            expectedGateCount,
        )
        mediator.tell(
            Publish(
                GATE_DRAIN_TOPIC,
                GateDrainCommand.newBuilder()
                    .setPlanId(command.planId)
                    .setCoordinatorActor(self.encodeActorRef(node.system))
                    .build(),
            ),
            self,
        )
        if (expectedGateCount == 0) {
            beginWorldShutdown()
        }
        sender.tell(status(), self)
    }

    private fun handleGateDrainAck(ack: GateDrainAck) {
        if (ack.planId != planId || phase != ShutdownPhase.SHUTDOWN_PHASE_DRAINING_GATES) {
            return
        }
        drainedGateNodes += ack.gateNodeId
        expectedPlayerIds += ack.playerIdList
        logger.info(
            "gate drained planId={} gateNodeId={} players={} drainedGateProgress={}",
            ack.planId,
            ack.gateNodeId,
            ack.playerIdCount,
            "${drainedGateNodes.size}/$expectedGateCount",
        )
        if (drainedGateNodes.size >= expectedGateCount) {
            enterPhase(ShutdownPhase.SHUTDOWN_PHASE_DRAINING_PLAYERS)
            if (expectedPlayerIds.all { it in flushedPlayerIds }) {
                beginWorldShutdown()
            }
        }
    }

    private fun handlePlayerShutdownAck(ack: PlayerShutdownAck) {
        if (
            ack.shutdownPlanId != planId ||
            phase !in setOf(
                ShutdownPhase.SHUTDOWN_PHASE_DRAINING_GATES,
                ShutdownPhase.SHUTDOWN_PHASE_DRAINING_PLAYERS,
            )
        ) {
            return
        }
        if (ack.success) {
            flushedPlayerIds += ack.playerId
        } else {
            errors += "player ${ack.playerId} shutdown failed: ${ack.error}"
            enterPhase(ShutdownPhase.SHUTDOWN_PHASE_FAILED)
            return
        }
        logger.info(
            "player shutdown ack planId={} playerId={} flushedPlayerCount={}/{}",
            ack.shutdownPlanId,
            ack.playerId,
            flushedPlayerIds.size,
            expectedPlayerIds.size,
        )
        if (
            phase == ShutdownPhase.SHUTDOWN_PHASE_DRAINING_PLAYERS &&
            expectedPlayerIds.all { it in flushedPlayerIds }
        ) {
            beginWorldShutdown()
        }
    }

    private fun beginWorldShutdown() {
        enterPhase(ShutdownPhase.SHUTDOWN_PHASE_STOPPING_WORLDS)
        expectedWorldIds.clear()
        expectedWorldIds += node.gameWorldIds
        logger.info("world shutdown started planId={} expectedWorldCount={}", planId, expectedWorldIds.size)
        if (expectedWorldIds.isEmpty()) {
            enterPhase(ShutdownPhase.SHUTDOWN_PHASE_COMPLETED)
            return
        }
        expectedWorldIds.forEach { worldId ->
            node.worldSharding.tell(
                WorldShutdownReq.newBuilder()
                    .setWorldId(worldId)
                    .setShutdownPlanId(requireNotNull(planId))
                    .setCoordinatorActor(self.encodeActorRef(node.system))
                    .build(),
                self,
            )
        }
    }

    private fun handleWorldShutdownAck(ack: WorldShutdownAck) {
        if (ack.shutdownPlanId != planId || phase != ShutdownPhase.SHUTDOWN_PHASE_STOPPING_WORLDS) {
            return
        }
        if (ack.success) {
            flushedWorldIds += ack.worldId
        } else {
            errors += "world ${ack.worldId} shutdown failed: ${ack.error}"
            enterPhase(ShutdownPhase.SHUTDOWN_PHASE_FAILED)
            return
        }
        logger.info(
            "world shutdown ack planId={} worldId={} flushedWorldCount={}/{}",
            ack.shutdownPlanId,
            ack.worldId,
            flushedWorldIds.size,
            expectedWorldIds.size,
        )
        if (expectedWorldIds.all { it in flushedWorldIds }) {
            enterPhase(ShutdownPhase.SHUTDOWN_PHASE_COMPLETED)
            logger.info("shutdown plan completed planId={}", planId)
        }
    }

    private fun handlePhaseTimeout(timeout: ShutdownPhaseTimeout) {
        if (!timeout.isCurrent()) {
            return
        }
        val error = when (phase) {
            ShutdownPhase.SHUTDOWN_PHASE_DRAINING_GATES ->
                "gate drain timeout: drained ${drainedGateNodes.size}/$expectedGateCount gate nodes"

            ShutdownPhase.SHUTDOWN_PHASE_DRAINING_PLAYERS -> {
                val missing = expectedPlayerIds - flushedPlayerIds
                "player shutdown timeout: flushed ${flushedPlayerIds.size}/${expectedPlayerIds.size} " +
                        "missing=${missing.formatIds()}"
            }

            ShutdownPhase.SHUTDOWN_PHASE_STOPPING_WORLDS -> {
                val missing = expectedWorldIds - flushedWorldIds
                "world shutdown timeout: flushed ${flushedWorldIds.size}/${expectedWorldIds.size} " +
                        "missing=${missing.formatIds()}"
            }

            else -> "shutdown timeout at phase=$phase"
        }
        errors += error
        logger.error("shutdown plan failed planId={} phase={} error={}", planId, phase, error)
        enterPhase(ShutdownPhase.SHUTDOWN_PHASE_FAILED)
    }

    private fun ShutdownPhaseTimeout.isCurrent(): Boolean {
        return planId == this@ShutdownCoordinatorActor.planId &&
                phase == this@ShutdownCoordinatorActor.phase &&
                generation == this@ShutdownCoordinatorActor.generation &&
                !this@ShutdownCoordinatorActor.phase.isTerminal()
    }

    private fun enterPhase(nextPhase: ShutdownPhase) {
        phase = nextPhase
        schedulePhaseTimeout(nextPhase)
    }

    private fun schedulePhaseTimeout(nextPhase: ShutdownPhase) {
        if (nextPhase.isTerminal()) {
            return
        }
        val currentPlanId = planId ?: return
        val timeout = timeoutFor(nextPhase) ?: return
        context.system.scheduler().scheduleOnce(
            timeout,
            self,
            ShutdownPhaseTimeout(currentPlanId, generation, nextPhase),
            context.dispatcher,
            self,
        )
    }

    private fun timeoutFor(currentPhase: ShutdownPhase): Duration? {
        return when (currentPhase) {
            ShutdownPhase.SHUTDOWN_PHASE_DRAINING_GATES -> gateDrainTimeout
            ShutdownPhase.SHUTDOWN_PHASE_DRAINING_PLAYERS -> playerDrainTimeout
            ShutdownPhase.SHUTDOWN_PHASE_STOPPING_WORLDS -> worldStopTimeout
            else -> null
        }
    }

    private fun reset(command: ShutdownStartReq) {
        generation += 1
        planId = command.planId
        requestedBy = command.requestedBy
        expectedGateCount = 0
        drainedGateNodes.clear()
        expectedPlayerIds.clear()
        flushedPlayerIds.clear()
        expectedWorldIds.clear()
        flushedWorldIds.clear()
        errors.clear()
    }

    private fun activeRoleMemberCount(role: String): Int {
        return Cluster.get(context.system).state().members.count { member ->
            member.hasRole(role) && member.status() in setOf(MemberStatus.up(), MemberStatus.weaklyUp())
        }
    }

    private fun status(): ShutdownStatusResp {
        val builder = ShutdownStatusResp.newBuilder()
            .setPhase(phase)
            .setExpectedGateCount(expectedGateCount)
            .setDrainedGateCount(drainedGateNodes.size)
            .setExpectedPlayerCount(expectedPlayerIds.size)
            .setFlushedPlayerCount(flushedPlayerIds.size)
            .setExpectedWorldCount(expectedWorldIds.size)
            .setFlushedWorldCount(flushedWorldIds.size)
            .addAllErrors(errors)
        planId?.let(builder::setPlanId)
        requestedBy?.let(builder::setRequestedBy)
        return builder.build()
    }
}

private data class ShutdownPhaseTimeout(
    val planId: String,
    val generation: Long,
    val phase: ShutdownPhase,
)

private fun ShutdownPhase.isTerminal(): Boolean {
    return this in setOf(
        ShutdownPhase.SHUTDOWN_PHASE_IDLE,
        ShutdownPhase.SHUTDOWN_PHASE_COMPLETED,
        ShutdownPhase.SHUTDOWN_PHASE_FAILED,
    )
}

private fun Collection<Long>.formatIds(limit: Int = 20): String {
    if (isEmpty()) {
        return "[]"
    }
    val values = take(limit).joinToString(prefix = "[", postfix = "]")
    return if (size <= limit) {
        values
    } else {
        values.dropLast(1) + ", ... +${size - limit}]"
    }
}

private fun com.typesafe.config.Config.getDurationOrDefault(path: String, defaultValue: Duration): Duration {
    return if (hasPath(path)) getDuration(path) else defaultValue
}
