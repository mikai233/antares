package com.mikai233.gate.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.common.inject.XKoin
import com.mikai233.gate.GateSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.WorldShardNum
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage
import com.mikai233.shared.message.SerdeWorldMessage
import com.mikai233.shared.message.WorldMessageExtractor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GateSharding(private val koin: XKoin) : KoinComponent by koin {
    private val akka: AkkaSystem<GateSystemMessage> by inject()
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActor: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    init {
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
