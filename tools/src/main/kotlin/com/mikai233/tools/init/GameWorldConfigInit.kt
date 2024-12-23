package com.mikai233.tools.init

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.config.WorldConfig
import com.mikai233.common.core.component.config.WorldData

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/21
 */
fun main() {
    val configCenter = ZookeeperConfigCenter()
    with(configCenter) {
        createGameWorld()
    }
}

internal fun ZookeeperConfigCenter.createGameWorld() {
    val gameWorlds = listOf(
        WorldConfig(1000, 0, emptyList(), WorldData("world1000", "${GlobalEnv.machineIp}:${GlobalEnv.loginPort}")),
        WorldConfig(1001, 0, emptyList(), WorldData("world1001", "${GlobalEnv.machineIp}:${GlobalEnv.loginPort}"))
    )
    gameWorlds.forEach { worldConfig ->
        addConfig(worldConfig)
    }
}
