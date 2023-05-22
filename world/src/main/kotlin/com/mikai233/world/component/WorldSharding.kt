package com.mikai233.world.component

import akka.actor.typed.ActorRef
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Component
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.ext.startSharding
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.WorldShardNum
import com.mikai233.shared.message.*
import com.mikai233.world.WorldActor
import com.mikai233.world.WorldNode
import com.mikai233.world.WorldSystemMessage

class WorldSharding(private val worldNode: WorldNode) : Component {
    private lateinit var akka: AkkaSystem<WorldSystemMessage>
    private val server = worldNode.server
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActor: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    override fun init() {
        akka = server.component()
        startSharding()
        startShardingProxy()
    }

    private fun startSharding() {
        val system = akka.system
        worldActor = system.startSharding(
            ShardEntityType.WorldActor.name,
            Role.World,
            WorldMessageExtractor(WorldShardNum),
            StopWorld,
            ClusterShardingSettings.create(system).withNoPassivationStrategy(),
        ) { entityCtx ->
            val behavior = Behaviors.setup<WorldMessage> { ctx ->
                Behaviors.withStash(100) { buffer ->
                    Behaviors.withTimers { timers ->
                        WorldActor(ctx, buffer, timers, entityCtx.entityId.toLong(), worldNode)
                    }
                }
            }
            Behaviors.supervise(behavior).onFailure(SupervisorStrategy.resume())
        }
    }

    private fun startShardingProxy() {
        val system = akka.system
        playerActor = system.startShardingProxy(
            ShardEntityType.PlayerActor.name,
            Role.World,
            PlayerMessageExtractor(PlayerShardNum)
        )
    }
}