package com.mikai233.tools.init

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.config.*

private var PORT_ALLOC = 2333
fun nextPort() = PORT_ALLOC++

fun main() {
    val configCenter = ZookeeperConfigCenter()
    with(configCenter) {
        createServerHosts("stardust")
        createNettyConfig()
        createDataSource()
    }
}

private fun createServerNodes(host: String): List<Node> {
    return listOf(
        Node(host, Role.Player, nextPort(), true),
        Node(host, Role.Gate, nextPort(), true),
        Node(host, Role.Global, nextPort(), false),
        Node(host, Role.World, nextPort(), false),
        Node(host, Role.Player, nextPort(), true),
        Node(host, Role.Gm, nextPort(), false),
        Node(host, Role.Gm, nextPort(), false)
    )
}

internal fun ZookeeperConfigCenter.createServerHosts(systemName: String) {
    val serverHosts = ServerHosts(systemName)
    addConfig(serverHosts)
    createServerNodes(GlobalEnv.machineIp).forEach { node ->
        addConfig(node)
    }
}

internal fun ZookeeperConfigCenter.createNettyConfig() {
    val nettyConfig = NettyConfig(GlobalEnv.machineIp, GlobalEnv.loginPort)
    addConfig(nettyConfig)
}

internal fun ZookeeperConfigCenter.createDataSource() {
    val gameDataSource = GameDataSource(listOf(Source("127.0.0.1", 27017)))
    addConfig(gameDataSource)
}
