package com.mikai233.gm.component

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.inject.XKoin
import com.mikai233.gm.GmSystemMessage
import com.mikai233.shared.message.SerdePlayerMessage
import com.mikai233.shared.message.SerdeWorldMessage
import com.mikai233.shared.startPlayerActorShardingProxy
import com.mikai233.shared.startWorldActorShardingProxy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GmSharding(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<GmSystemMessage> by inject()
    lateinit var playerActorSharding: ActorRef<ShardingEnvelope<SerdePlayerMessage>>
        private set
    lateinit var worldActorSharding: ActorRef<ShardingEnvelope<SerdeWorldMessage>>
        private set

    init {
        startShardingProxy()
    }

    private fun startShardingProxy() {
        playerActorSharding = akkaSystem.system.startPlayerActorShardingProxy()
        worldActorSharding = akkaSystem.system.startWorldActorShardingProxy()
    }
}
