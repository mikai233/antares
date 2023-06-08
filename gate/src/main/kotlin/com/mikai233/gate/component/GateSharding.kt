package com.mikai233.gate.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.inject.XKoin
import com.mikai233.gate.GateSystemMessage
import com.mikai233.shared.message.SerdePlayerMessage
import com.mikai233.shared.message.SerdeWorldMessage
import com.mikai233.shared.startPlayerActorShardingProxy
import com.mikai233.shared.startWorldActorShardingProxy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GateSharding(private val koin: XKoin) : KoinComponent by koin {
    private val akka: AkkaSystem<GateSystemMessage> by inject()
    lateinit var playerActorSharding: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActorSharding: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    init {
        startShardingProxy()
    }

    private fun startShardingProxy() {
        playerActorSharding = akka.system.startPlayerActorShardingProxy()
        worldActorSharding = akka.system.startWorldActorShardingProxy()
    }
}
