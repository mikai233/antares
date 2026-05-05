package com.mikai233.gate

import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.message.global.shutdown.GATE_DRAIN_TOPIC
import com.mikai233.common.runtime.system
import com.mikai233.protocol.ProtoRpcShutdown.GateDrainAck
import com.mikai233.protocol.ProtoRpcShutdown.GateDrainCommand
import io.github.realmlabs.asteria.actor.AsteriaActor
import org.apache.pekko.actor.Props
import org.apache.pekko.cluster.pubsub.DistributedPubSub
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.Subscribe

class GateShutdownListenerActor(val node: GateNode) : AsteriaActor<GateNode>(node) {
    companion object {
        const val Name = "gateShutdownListener"

        fun props(node: GateNode): Props = Props.create(GateShutdownListenerActor::class.java, node)
    }

    private val mediator = DistributedPubSub.get(context.system).mediator()

    override fun preStart() {
        super.preStart()
        mediator.tell(Subscribe(GATE_DRAIN_TOPIC, self), self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(GateDrainCommand::class.java) { handleGateDrain(it) }
            .build()
    }

    private fun handleGateDrain(command: GateDrainCommand) {
        val coordinator = command.coordinatorActor.decodeActorRef(node.system)
        node.connectionDrainer.beginDrain(
            planId = command.planId,
            coordinator = coordinator,
            reason = "shutdown plan ${command.planId}",
        )
        val playerIds = node.connectionDrainer.activePlayerIds
        node.connectionDrainer.closeAll()
        coordinator.tell(
            GateDrainAck.newBuilder()
                .setPlanId(command.planId)
                .setGateNodeId(node.nodeId)
                .addAllPlayerId(playerIds)
                .build(),
            self,
        )
    }
}
