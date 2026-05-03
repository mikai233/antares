package com.mikai233.gate

import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.message.StopChannel
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.gateway.GatewayCloseReason
import io.github.realmlabs.asteria.gateway.GatewayConnection
import io.github.realmlabs.asteria.gateway.GatewayFrame
import io.github.realmlabs.asteria.gateway.GatewaySession
import io.github.realmlabs.asteria.gateway.GatewaySessionAttributeKey
import io.github.realmlabs.asteria.gateway.GatewaySessionId
import io.github.realmlabs.asteria.gateway.GatewayTransportHandler
import org.apache.pekko.actor.ActorRef

val GateChannelActorKey: GatewaySessionAttributeKey<ActorRef> = GatewaySessionAttributeKey("gate.channelActor")

class GateTransportHandler(private val node: GateNode) : GatewayTransportHandler {
    private val logger = logger()

    override suspend fun connected(connection: GatewayConnection): GatewaySession {
        val session = GatewaySession(GatewaySessionId(connection.id.value), connection)
        if (node.state == NodeState.Started) {
            val channelActor = node.system.actorOf(ChannelActor.props(node, session))
            session.set(GateChannelActorKey, channelActor)
        } else {
            logger.warn("gate is not running, current state:{}, session will close", node.state)
            session.close(GatewayCloseReason.Application)
        }
        return session
    }

    override suspend fun received(session: GatewaySession, frame: GatewayFrame) {
        session.markRead()
        val channelActor = session.get(GateChannelActorKey)
        if (channelActor == null) {
            logger.warn("failed to forward frame because channel actor not found, session:{}", session.id.value)
            session.close(GatewayCloseReason.Application)
            return
        }
        val message = node.protocolCodec.decodeClient(frame)
        channelActor.tell(message)
    }

    override suspend fun disconnected(session: GatewaySession, cause: Throwable?) {
        val reason = if (cause == null) {
            GatewayCloseReason.TransportInactive
        } else {
            GatewayCloseReason.error(cause)
        }
        session.markClosed(reason)
        session.get(GateChannelActorKey)?.tell(StopChannel)
    }
}
