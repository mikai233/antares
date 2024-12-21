package com.mikai233.world.component

import akka.actor.typed.ActorRef
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.extension.startSharding
import com.mikai233.common.inject.XKoin
import com.mikai233.shared.WorldShardNum
import com.mikai233.shared.message.*
import com.mikai233.shared.startPlayerActorShardingProxy
import com.mikai233.world.WorldActor
import com.mikai233.world.WorldSystemMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorldSharding(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<WorldSystemMessage> by inject()
    lateinit var playerActorSharding: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActorSharding: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    init {
        startSharding()
        startShardingProxy()
    }

    private fun startSharding() {
        val system = akkaSystem.system
        worldActorSharding = system.startSharding(
            ShardEntityType.WorldActor.name,
            Role.World,
            WorldMessageExtractor(WorldShardNum),
            StopWorld,
            ClusterShardingSettings.create(system).withNoPassivationStrategy(),
        ) { entityCtx ->
            val behavior = Behaviors.setup<WorldMessage> { ctx ->
                Behaviors.withStash(100) { buffer ->
                    Behaviors.withTimers { timers ->
                        WorldActor(ctx, buffer, timers, entityCtx.entityId.toLong(), koin)
                    }
                }
            }
            Behaviors.supervise(behavior).onFailure(SupervisorStrategy.resume())
        }
    }

    private fun startShardingProxy() {
        playerActorSharding = akkaSystem.system.startPlayerActorShardingProxy()
    }
}
