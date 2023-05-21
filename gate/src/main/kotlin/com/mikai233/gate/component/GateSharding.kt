package com.mikai233.gate.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.Component
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ShardEntityType
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.gate.GateSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.WorldShardNum
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage
import com.mikai233.shared.message.SerdeWorldMessage
import com.mikai233.shared.message.WorldMessageExtractor

class GateSharding(private val server: Server) : Component {
    private lateinit var akka: AkkaSystem<GateSystemMessage>
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActor: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    override fun init() {
        akka = server.component()
        startShardingProxy()
    }

    private fun startShardingProxy() {
        val system = akka.system
        playerActor = system.startShardingProxy(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(PlayerShardNum)
        )
        worldActor = system.startShardingProxy(
            ShardEntityType.WorldActor.name,
            Role.World,
            WorldMessageExtractor(WorldShardNum)
        )
    }
}