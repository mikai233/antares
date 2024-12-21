package com.mikai233.common.core.component

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.config.*
import com.mikai233.common.extension.logger
import com.mikai233.common.inject.XKoin
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/11
 */
open class NodeConfigHolder(
    private val koin: XKoin,
    private val role: Role,
    private val port: Int,
    private val sameJvm: Boolean,
) : KoinComponent by koin {
    private val logger = logger()
    private val configCenter: ZookeeperConfigCenter by inject()
    lateinit var selfNode: Node
        private set
    lateinit var akkaSystemName: String
        private set

    init {
        initSelfNode()
        initSysName()
    }

    open fun retrieveAkkaConfig(): Config {
        val node = selfNode
        val configs = mutableMapOf(
            "akka.cluster.roles" to listOf(node.role.name),
            "akka.remote.artery.canonical.hostname" to node.host,
            "akka.remote.artery.canonical.port" to node.port,
            "akka.cluster.seed-nodes" to getAllSeedNodes().map {
                formatSeedNode(
                    akkaSystemName,
                    it.host,
                    it.port
                )
            },
            "akka.cluster.auto-down-unreachable-after" to "off",
        )
        if (sameJvm) {
            configs["akka.cluster.jmx.multi-mbeans-in-same-jvm"] = "on"
        }
        val centerConfig = ConfigFactory.parseMap(configs)
        val roleConfig = ConfigFactory.load("${node.role.name.lowercase()}.conf")
        return centerConfig.withFallback(roleConfig)
    }

    private fun getAllNodes(): Map<String, List<Node>> {
        val allNodes = mutableMapOf<String, MutableList<Node>>()
        with(configCenter) {
            val serverHostsPath = serverHostsPath()
            val nodes = mutableListOf<Node>()
            getChildren(serverHostsPath).forEach { hostPath ->
                val hostFullPath = serverHostPath(hostPath)
                getChildren(hostFullPath).forEach { nodePath ->
                    val nodeFullPath = nodePath(hostPath, nodePath)
                    nodes.add(getConfigEx<Node>(nodeFullPath))
                }
                allNodes[hostPath] = nodes
            }
        }
        return allNodes
    }

    private fun initSelfNode() {
        val path = nodePath(GlobalEnv.machineIp, role, port)
        selfNode = configCenter.getConfigEx(path)
    }

    private fun initSysName() {
        akkaSystemName = configCenter.getConfigEx<ServerHosts>(ServerHosts.PATH).systemName
    }

    private fun getAllSeedNodes() = getAllNodes().values.flatten().filter { it.seed }

    private fun formatSeedNode(systemName: String, host: String, port: Int) = "akka://$systemName@$host:$port"
}
