package com.mikai233.world.component

import akka.cluster.Cluster
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Component
import com.mikai233.common.core.component.WorldConfigComponent
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.shardingEnvelope
import com.mikai233.shared.message.WakeupGameWorld
import com.mikai233.world.WorldNode
import com.mikai233.world.WorldSystemMessage

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/21
 */
class WorldWaker(private val worldNode: WorldNode) : Component {
    private val logger = logger()
    private lateinit var akkaSystem: AkkaSystem<WorldSystemMessage>
    private lateinit var cluster: Cluster
    private lateinit var worldSharding: WorldSharding
    private lateinit var worldConfigComponent: WorldConfigComponent
    override fun init() {
        akkaSystem = worldNode.server.component()
        worldSharding = worldNode.server.component()
        worldConfigComponent = worldNode.server.component()
        cluster = Cluster.get(akkaSystem.system)
        cluster.registerOnMemberUp(::wakeupWorld)
    }

    private fun wakeupWorld() {
        logger.info("start wakeup world")
        worldConfigComponent.getWorldConfig().forEach { (worldId, worldConfig) ->
            worldSharding.worldActor.tell(shardingEnvelope("$worldId", WakeupGameWorld))
        }
    }
}