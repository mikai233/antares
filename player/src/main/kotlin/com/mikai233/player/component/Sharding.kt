package com.mikai233.player.component

import akka.actor.typed.ActorRef
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Component
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.ext.startSharding
import com.mikai233.player.PlayerActor
import com.mikai233.player.PlayerNode
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.message.*

class Sharding(private val playerNode: PlayerNode) : Component {
    private lateinit var akka: AkkaSystem<PlayerSystemMessage>
    private val server = playerNode.server
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActor: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    override fun init() {
        akka = server.component()
        startSharding()
    }

    private fun startSharding() {
        val system = akka.system
        playerActor = system.startSharding(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(PlayerShardNum),
            StopPlayer
        ) { entityCtx ->
            val behavior = Behaviors.setup<PlayerMessage> { ctx ->
                Behaviors.withStash(100) { buffer ->
                    PlayerActor(ctx, buffer, entityCtx.entityId.toLong(), playerNode)
                }
            }
            Behaviors.supervise(behavior).onFailure(SupervisorStrategy.resume())
        }
    }
}