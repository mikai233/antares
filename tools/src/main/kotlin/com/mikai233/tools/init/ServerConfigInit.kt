package com.mikai233.tools.init

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenter
import com.mikai233.common.core.components.config.NettyConfig
import com.mikai233.common.core.components.config.Node
import com.mikai233.common.core.components.config.ServerHosts

private var PORT_ALLOC = 2333
fun nextPort() = PORT_ALLOC++
fun main() {
    val configCenter = ZookeeperConfigCenter()
    configCenter.init()
    with(configCenter) {
        createServerHosts("stardust")
        createNettyConfig()
    }
}

private fun createServerNodes(host: String): List<Node> {
    return listOf(
        Node(host, Role.Player, nextPort(), true),
        Node(host, Role.Gate, nextPort(), true),
        Node(host, Role.Global, nextPort(), false),
        Node(host, Role.World, nextPort(), false)
    )
}

private fun ZookeeperConfigCenter.createServerHosts(systemName: String) {
    val serverHosts = ServerHosts(systemName)
    addConfig(serverHosts)
    createServerNodes(GlobalEnv.machineIp).forEach { node ->
        addConfig(node)
    }
}

private fun ZookeeperConfigCenter.createNettyConfig() {
    val nettyConfig = NettyConfig(GlobalEnv.machineIp, 6666)
    addConfig(nettyConfig)
}