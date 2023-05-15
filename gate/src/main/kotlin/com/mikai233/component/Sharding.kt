package com.mikai233.component

import akka.actor.typed.ActorRef
import com.mikai233.GateSystemMessage
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Cluster
import com.mikai233.common.core.components.Component
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ShardEntityType
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.shared.message.PlayerMessage
import com.mikai233.shared.message.PlayerMessageExtractor

class Sharding(private val server: Server) : Component {
    private lateinit var cluster: Cluster<GateSystemMessage>
    lateinit var playerActorRef: ActorRef<PlayerMessage>
        private set

    override fun init() {
        cluster = server.component()
        startSharding()
    }

    private fun startSharding() {
        val system = cluster.system
        playerActorRef = system.startShardingProxy<PlayerMessage>(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(1000)
        )
    }

    override fun shutdown() {

    }
}