package com.mikai233.player.component

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.Behaviors
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Cluster
import com.mikai233.common.core.components.Component
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ShardEntityType
import com.mikai233.common.ext.startSharding
import com.mikai233.player.PlayerActor
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.shared.message.PlayerMessage
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.StopPlayer

class Sharding(private val server: Server) : Component {
    private lateinit var cluster: Cluster<PlayerSystemMessage>
    lateinit var playerActorRef: ActorRef<PlayerMessage>
    override fun init() {
        cluster = server.component()
        startSharding()
    }

    private fun startSharding() {
        val system = cluster.system
        playerActorRef = system.startSharding(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(1000),
            StopPlayer
        ) { entityCtx ->
            Behaviors.setup { ctx ->
                PlayerActor(ctx, entityCtx.entityId.toLong())
            }
        }
    }

    override fun shutdown() {

    }
}