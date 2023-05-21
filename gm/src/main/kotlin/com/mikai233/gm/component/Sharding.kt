package com.mikai233.gm.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.Server
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Component
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.gm.GmSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage

class Sharding(private val server: Server) : Component {
    private lateinit var akka: AkkaSystem<GmSystemMessage>
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set

    override fun init() {
        akka = server.component()
        startSharding()
    }

    private fun startSharding() {
        val system = akka.system
        playerActor = system.startShardingProxy(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(PlayerShardNum)
        )
    }
}