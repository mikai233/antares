package com.mikai233.common.core.component.config

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/21
 */
data class WorldConfig(val worldId: Int, val parentWorld: Int, val children: List<Int>, val data: WorldData) : Config {
    override fun path(): String {
        return gameWorldPath(worldId.toString())
    }
}

data class WorldData(val name: String, val loginAddress: String)