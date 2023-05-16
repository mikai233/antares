package com.mikai233.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.GateSystemMessage
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.Component
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ShardEntityType
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage

class Sharding(private val server: Server) : Component {
    private lateinit var akka: AkkaSystem<GateSystemMessage>
    lateinit var playerActorRef: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set

    override fun init() {
        akka = server.component()
        startSharding()
    }

    private fun startSharding() {
        val system = akka.system
        playerActorRef = system.startShardingProxy(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(1000)
        )
    }

    override fun shutdown() {

    }
}