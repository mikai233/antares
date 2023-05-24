package com.mikai233.gm.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.ext.startShardingProxy
import com.mikai233.common.inject.XKoin
import com.mikai233.gm.GmSystemMessage
import com.mikai233.shared.PlayerShardNum
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GmSharding(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<GmSystemMessage> by inject()
    lateinit var playerActor: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set

    init {
        startSharding()
    }

    private fun startSharding() {
        val system = akkaSystem.system
        playerActor = system.startShardingProxy(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerMessageExtractor(PlayerShardNum)
        )
    }
}