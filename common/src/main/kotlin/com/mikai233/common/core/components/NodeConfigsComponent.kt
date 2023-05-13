package com.mikai233.common.core.components

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.config.*
import com.mikai233.common.ext.logger
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/11
 */
class NodeConfigsComponent(private val role: Role, private val port: Int) : Component {
    private val logger = logger()
    private lateinit var server: Server
    private lateinit var configCenter: ZookeeperConfigCenterComponent
    private lateinit var selfNode: Node
    lateinit var akkaSystemName: String
        private set

    override fun init(server: Server) {
        this.server = server
        configCenter = server.component()
        initSelfNode()
        initSysName()
    }

    fun retrieveAkkaConfig(): Config {
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
        val centerConfig = ConfigFactory.parseMap(configs)
        val roleConfig = ConfigFactory.load("${node.role.name}.conf")
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
        val path = nodePath(GlobalEnv.MachineIp, role, port)
        selfNode = configCenter.getConfigEx(path)
    }

    private fun initSysName() {
        akkaSystemName = configCenter.getConfigEx<ServerHosts>(ServerHosts.PATH).systemName
    }

    private fun getAllSeedNodes() = getAllNodes().values.flatten().filter { it.seed }

    private fun formatSeedNode(systemName: String, host: String, port: Int) = "akka://$systemName@$host:$port"

    override fun shutdown() {

    }
}