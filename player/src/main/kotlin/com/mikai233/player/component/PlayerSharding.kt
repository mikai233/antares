package com.mikai233.player.component

import com.mikai233.common.extension.startSharding
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.player.PlayerActor
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.WorldShardNum
import com.mikai233.shared.message.*

class PlayerSharding() : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<PlayerSystemMessage> by inject()
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
        playerActorSharding = system.startSharding(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(PlayerShardNum),
            StopPlayer
        ) { entityCtx ->
            val behavior = Behaviors.setup<PlayerMessage> { ctx ->
                Behaviors.withStash(100) { buffer ->
                    Behaviors.withTimers { timers ->
                        PlayerActor(ctx, buffer, timers, entityCtx.entityId.toLong(), koin)
                    }
                }
            }
            Behaviors.supervise(behavior).onFailure(SupervisorStrategy.resume())
        }
    }

    private fun startShardingProxy() {
        val system = akkaSystem.system
        worldActorSharding = system.startShardingProxy(
            ShardEntityType.WorldActor.name,
            Role.World,
            WorldMessageExtractor(WorldShardNum)
        )
    }
}
