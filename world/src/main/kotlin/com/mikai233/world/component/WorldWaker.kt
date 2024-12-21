package com.mikai233.world.component

import akka.cluster.Cluster
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.WorldConfigHolder
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.shardingEnvelope
import com.mikai233.common.inject.XKoin
import com.mikai233.shared.message.WakeupGameWorld
import com.mikai233.world.WorldSystemMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/21
 */
class WorldWaker(private val koin: XKoin) : KoinComponent by koin {
    private val logger = logger()
    private val akkaSystem: AkkaSystem<WorldSystemMessage> by inject()
    private val cluster: Cluster = Cluster.get(akkaSystem.system)
    private val worldSharding: WorldSharding by inject()
    private val worldConfigHolder: WorldConfigHolder by inject()

    init {
        cluster.registerOnMemberUp(::wakeupWorld)
    }

    private fun wakeupWorld() {
        logger.info("start wakeup world")
        worldConfigHolder.getWorldConfig().forEach { (worldId, worldConfig) ->
            worldSharding.worldActorSharding.tell(shardingEnvelope("$worldId", WakeupGameWorld))
        }
    }
}
