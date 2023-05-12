package com.mikai233.tools.init

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.config.Host
import com.mikai233.common.core.components.config.Node
import com.mikai233.common.core.components.config.ServerHosts
import com.mikai233.common.core.components.config.ZookeeperConfigCenterComponent

private var PORT_ALLOC = 2333
fun nextPort() = PORT_ALLOC++
fun main() {
    val server = Server()
    val configCenter = ZookeeperConfigCenterComponent()
    configCenter.init(server)
    with(configCenter) {
        createServerHosts("stardust")
    }
}

private fun createServerNodes(host: Host): List<Node> {
    return listOf(
        Node(host, Role.Player, nextPort(), true),
        Node(host, Role.Gate, nextPort(), true),
        Node(host, Role.Global, nextPort(), false),
        Node(host, Role.World, nextPort(), false)
    )
}

private fun ZookeeperConfigCenterComponent.createServerHosts(systemName: String) {
    val serverHosts = ServerHosts(systemName)
    addConfig(serverHosts)
    val ip = GlobalEnv.machineIp
    val host = Host(ip)
    addConfig(host)
    createServerNodes(host).forEach { node ->
        addConfig(node)
    }
}