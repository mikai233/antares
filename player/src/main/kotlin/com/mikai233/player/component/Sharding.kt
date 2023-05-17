package com.mikai233.player.component

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.Component
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ShardEntityType
import com.mikai233.common.ext.startSharding
import com.mikai233.player.PlayerActor
import com.mikai233.player.PlayerNode
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage
import com.mikai233.shared.message.StopPlayer

class Sharding(private val playerNode: PlayerNode) : Component {
    private lateinit var akka: AkkaSystem<PlayerSystemMessage>
    private val server = playerNode.server
    lateinit var playerActorRef: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
    override fun init() {
        akka = server.component()
        startSharding()
    }

    private fun startSharding() {
        val system = akka.system
        playerActorRef = system.startSharding(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(1000),
            StopPlayer
        ) { entityCtx ->
            Behaviors.setup { ctx ->
                Behaviors.withStash(100) { buffer ->
                    PlayerActor(ctx, buffer, entityCtx.entityId.toLong(), playerNode)
                }
            }
        }
    }

    override fun shutdown() {

    }
}