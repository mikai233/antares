package com.mikai233.stardust

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Role
import com.mikai233.common.core.config.NodeConfig
import com.mikai233.common.core.config.nodePath
import com.mikai233.common.core.config.serverHostsPath
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.common.extension.logger
import com.mikai233.gate.GateNode
import com.mikai233.global.GlobalNode
import com.mikai233.gm.GmNode
import com.mikai233.player.PlayerNode
import com.mikai233.world.WorldNode
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import java.net.InetSocketAddress
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

object StardustLauncher {
    private val logger = logger()
    private val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    private val nodeByRole: EnumMap<Role, KClass<out Launcher>> = EnumMap(Role::class.java)
    private val nodes: ArrayList<Launcher> = arrayListOf()

    init {
        nodeByRole[Role.Gate] = GateNode::class
        nodeByRole[Role.Player] = PlayerNode::class
        nodeByRole[Role.World] = WorldNode::class
        nodeByRole[Role.Gm] = GmNode::class
        nodeByRole[Role.Global] = GlobalNode::class
    }

    suspend fun launch() {
        val hostname = GlobalEnv.machineIp
        val hostPath = serverHostsPath(hostname)
        val nodeConfigs = coroutineScope {
            client.children.forPath(hostPath).await().map {
                val nodePath = nodePath(hostname, it)
                async {
                    val bytes = client.data.forPath(nodePath).await()
                    Json.fromBytes<NodeConfig>(bytes)
                }
            }.awaitAll()
        }.sortedBy { it.seed }
        val zookeeperConnectString = GlobalEnv.zkConnect
        val systemName = GlobalEnv.SYSTEM_NAME
        nodeConfigs.forEach { nodeConfig ->
            logger.info("{} launch node with config:{}", hostname, nodeConfig)
            val nodeClass = nodeByRole[nodeConfig.role]
            if (nodeClass != null) {
                val constructor =
                    requireNotNull(nodeClass.primaryConstructor) { "$nodeClass primaryConstructor not found" }
                val addr = InetSocketAddress(hostname, nodeConfig.port)
                val config = ConfigFactory.load("${nodeConfig.role.name.lowercase()}.conf")
                val sameJvm = true
                val node = constructor.call(addr, systemName, config, zookeeperConnectString, sameJvm)
                node.launch()
                nodes.add(node)
            } else {
                logger.error("node of role:{} not register", nodeConfig.role)
            }
        }
    }
}
