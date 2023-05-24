package com.mikai233.common.core.component

import com.mikai233.common.core.component.config.GAME_WORLD
import com.mikai233.common.core.component.config.WorldConfig
import com.mikai233.common.core.component.config.gameWorldPath
import com.mikai233.common.core.component.config.getConfigEx
import com.mikai233.common.inject.XKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/21
 */
class WorldConfigComponent(private val koin: XKoin) : KoinComponent by koin {
    private val configCenter: ZookeeperConfigCenter by inject()
    private val worldConfigs: MutableMap<Int, WorldConfig> = mutableMapOf()

    init {
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