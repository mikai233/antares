package com.mikai233.gate

import com.mikai233.common.message.ClientProtobuf
import com.mikai233.common.runtime.GameEntityKinds
import com.mikai233.common.runtime.system
import com.mikai233.gate.generated.GeneratedGatewayRouting
import com.mikai233.protocol.ProtoSystem.GmReq
import io.github.realmlabs.asteria.cluster.pekko.EntityShardRegistry
import io.github.realmlabs.asteria.cluster.pekko.SingletonActorRegistry
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.gateway.*
import io.github.realmlabs.asteria.gateway.pekko.PekkoGatewayForwarder
import io.github.realmlabs.asteria.gateway.pekko.PekkoGatewayLocalHandler
import io.github.realmlabs.asteria.gateway.pekko.PekkoGatewayMessageFactory
import io.github.realmlabs.asteria.message.RouteTarget
import org.apache.pekko.actor.ActorRef

val GatePlayerIdKey: GatewaySessionAttributeKey<Long> = GatewaySessionAttributeKey("gate.playerId")
val GateWorldIdKey: GatewaySessionAttributeKey<Long> = GatewaySessionAttributeKey("gate.worldId")

private val PlayerRouteTarget = RouteTarget.Entity(EntityKind(GameEntityKinds.PlayerActor))
private val WorldRouteTarget = RouteTarget.Entity(EntityKind(GameEntityKinds.WorldActor))
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

    fun dispatch(
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
    override fun resolve(context: GatewaySessionContext, packet: ClientProtobuf): GatewayRoute {
        GeneratedGatewayRouting.resolve(context, packet)?.let { return it }
        return when (val message = packet.message) {
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
        GeneratedGatewayRouting.entityMessage(context, route, packet)?.let { return it }
        val target = route.target as? RouteTarget.Entity
            ?: error("expected entity route target but got ${route.target}")
        return when (target.kind) {
            EntityKind(GameEntityKinds.PlayerActor) -> {
                when (val message = packet.message) {
                    is GmReq -> message.toBuilder()
                        .setPlayerId(route.entityId as Long)
                        .clearWorldId()
                        .build()

                    else -> error("unsupported player gateway message ${packet.message::class.qualifiedName}")
                }
            }

            EntityKind(GameEntityKinds.WorldActor) -> {
                when (val message = packet.message) {
                    is GmReq -> message.toBuilder()
                        .setPlayerId(requirePlayerId(context.session))
                        .setWorldId(route.entityId as Long)
                        .build()

                    else -> error("unsupported world gateway message ${packet.message::class.qualifiedName}")
                }
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
