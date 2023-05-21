package com.mikai233.common.core.component

import com.mikai233.common.core.Server
import com.mikai233.common.core.component.config.GAME_WORLD
import com.mikai233.common.core.component.config.WorldConfig
import com.mikai233.common.core.component.config.gameWorldPath
import com.mikai233.common.core.component.config.getConfigEx

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/21
 */
class WorldConfigComponent(private val server: Server) : Component {
    private lateinit var configCenter: ZookeeperConfigCenter
    private val worldConfigs: MutableMap<Int, WorldConfig> = mutableMapOf()
    override fun init() {
        configCenter = server.component()
        initWorldConfig()
    }

    private fun initWorldConfig() {
        with(configCenter) {
            getChildren(GAME_WORLD).forEach { worldId ->
                val worldConfig = getConfigEx<WorldConfig>(gameWorldPath(worldId))
                worldConfigs[worldConfig.worldId] = worldConfig
            }
        }
    }

    fun getWorldConfig() = worldConfigs.toMap()
}