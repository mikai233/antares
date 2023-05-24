package com.mikai233.player.component

import akka.actor.typed.ActorRef
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.ext.startSharding
import com.mikai233.common.inject.XKoin
import com.mikai233.player.PlayerActor
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.message.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PlayerSharding(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<PlayerSystemMessage> by inject()
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActor: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    init {
        startSharding()
    }

    private fun startSharding() {
        val system = akkaSystem.system
        playerActor = system.startSharding(
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
}