package com.mikai233.gate

import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.common.message.PlayerProtobufEnvelope
import com.mikai233.common.message.WorldProtobufEnvelope
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.ProtoTest.TestReq
import io.github.mikai233.asteria.cluster.pekko.EntityShardRegistry
import io.github.mikai233.asteria.cluster.pekko.SingletonActorRegistry
import io.github.mikai233.asteria.core.EntityKind
import io.github.mikai233.asteria.gateway.GatewayMessageDispatcher
import io.github.mikai233.asteria.gateway.GatewayRoute
import io.github.mikai233.asteria.gateway.GatewayRouteResolver
import io.github.mikai233.asteria.gateway.GatewaySession
import io.github.mikai233.asteria.gateway.GatewaySessionAttributeKey
import io.github.mikai233.asteria.gateway.GatewaySessionContext
import io.github.mikai233.asteria.gateway.pekko.PekkoGatewayForwarder
import io.github.mikai233.asteria.gateway.pekko.PekkoGatewayLocalHandler
import io.github.mikai233.asteria.gateway.pekko.PekkoGatewayMessageFactory
import io.github.mikai233.asteria.message.RouteTarget
import org.apache.pekko.actor.ActorRef

val GatePlayerIdKey: GatewaySessionAttributeKey<Long> = GatewaySessionAttributeKey("gate.playerId")
val GateWorldIdKey: GatewaySessionAttributeKey<Long> = GatewaySessionAttributeKey("gate.worldId")

private val PlayerRouteTarget = RouteTarget.Entity(EntityKind(ShardEntityType.PlayerActor.name))
private val WorldRouteTarget = RouteTarget.Entity(EntityKind(ShardEntityType.WorldActor.name))
private val GmCommandTargets = mapOf(
    "testGm" to PlayerRouteTarget,
    "testBroadcast" to WorldRouteTarget,
)

data class LocalClientProtobuf(
    val message: com.google.protobuf.GeneratedMessage,
)

class GateGatewayRouter(
    private val node: GateNode,
) {
    private val routeResolver = GateGatewayRouteResolver()
    private val messageFactory = GateGatewayMessageFactory()
    private val localHandler = GateGatewayLocalHandler()

    suspend fun dispatch(
        session: GatewaySession,
        packet: ClientProtobuf,
    ): GatewayRoute {
        return GatewayMessageDispatcher(
            routeResolver = routeResolver,
            forwarder = PekkoGatewayForwarder(
                system = node.system,
                shards = node.services.get(EntityShardRegistry::class),
                singletons = node.services.get(SingletonActorRegistry::class),
                messageFactory = messageFactory,
                localHandler = localHandler,
                sender = session.get(GateChannelActorKey),
            ),
        ).dispatch(GatewaySessionContext(session), packet)
    }
}

private class GateGatewayRouteResolver : GatewayRouteResolver<ClientProtobuf> {
    override suspend fun resolve(context: GatewaySessionContext, packet: ClientProtobuf): GatewayRoute {
        return when (val message = packet.message) {
            is PingReq -> GatewayRoute(RouteTarget.GatewayLocal)
            is TestReq -> GatewayRoute(PlayerRouteTarget, requirePlayerId(context.session))
            is GmReq -> resolveGmRoute(context.session, message)
            else -> error("no gateway route for packet id=${packet.id}, type=${packet.message::class.qualifiedName}")
        }
    }

    private fun resolveGmRoute(session: GatewaySession, request: GmReq): GatewayRoute {
        return when (GmCommandTargets[request.cmd]) {
            PlayerRouteTarget -> GatewayRoute(PlayerRouteTarget, requirePlayerId(session))
            WorldRouteTarget -> GatewayRoute(WorldRouteTarget, requireWorldId(session))
            null -> error("no gateway route for GM command=${request.cmd}")
            else -> error("unsupported gateway GM route for command=${request.cmd}")
        }
    }
}

private class GateGatewayMessageFactory : PekkoGatewayMessageFactory<ClientProtobuf> {
    override fun entityMessage(context: GatewaySessionContext, route: GatewayRoute, packet: ClientProtobuf): Any {
        val target = route.target as? RouteTarget.Entity
            ?: error("expected entity route target but got ${route.target}")
        return when (target.kind) {
            EntityKind(ShardEntityType.PlayerActor.name) -> {
                PlayerProtobufEnvelope(route.entityId as Long, packet.message)
            }

            EntityKind(ShardEntityType.WorldActor.name) -> {
                WorldProtobufEnvelope(
                    playerId = requirePlayerId(context.session),
                    worldId = route.entityId as Long,
                    message = packet.message,
                )
            }

            else -> error("unsupported gateway entity target ${target.kind.value}")
        }
    }

    override fun singletonMessage(context: GatewaySessionContext, route: GatewayRoute, packet: ClientProtobuf): Any {
        error("gate does not use singleton gateway routes")
    }

    override fun serviceMessage(context: GatewaySessionContext, route: GatewayRoute, packet: ClientProtobuf): Any {
        error("gate does not use service gateway routes")
    }

    override fun localMessage(context: GatewaySessionContext, route: GatewayRoute, packet: ClientProtobuf): Any {
        return LocalClientProtobuf(packet.message)
    }
}

private class GateGatewayLocalHandler : PekkoGatewayLocalHandler<ClientProtobuf> {
    override fun handle(context: GatewaySessionContext, route: GatewayRoute, packet: ClientProtobuf) {
        val channelActor = context.session.get(GateChannelActorKey)
            ?: error("channel actor not found for session ${context.session.id.value}")
        channelActor.tell(LocalClientProtobuf(packet.message), ActorRef.noSender())
    }
}

private fun requirePlayerId(session: GatewaySession): Long {
    return requireNotNull(session.get(GatePlayerIdKey)) {
        "playerId not bound for session ${session.id.value}"
    }
}

private fun requireWorldId(session: GatewaySession): Long {
    return requireNotNull(session.get(GateWorldIdKey)) {
        "worldId not bound for session ${session.id.value}"
    }
}
