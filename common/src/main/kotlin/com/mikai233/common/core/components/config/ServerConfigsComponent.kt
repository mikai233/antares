package com.mikai233.common.core.components.config

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Component
import com.mikai233.common.core.components.Role
import com.mikai233.common.ext.logger
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/11
 */
class ServerConfigsComponent(private val role: Role, private val port: Int) : Component {
    private val logger = logger()
    private lateinit var server: Server
    private lateinit var zookeeperConfigCenterComponent: ZookeeperConfigCenterComponent
    private lateinit var selfNode: Node
    lateinit var akkaSystemName: String
        private set

    override fun init(server: Server) {
        this.server = server
        zookeeperConfigCenterComponent = server.component()
        initSelfNode()
        initSysName()
    }

    fun retrieveAkkaConfig(): Config {
        val node = selfNode
        val configs = mutableMapOf(
            "akka.cluster.roles" to listOf(node.role.name),
            "akka.remote.artery.canonical.hostname" to node.host.address,
            "akka.remote.artery.canonical.port" to node.port,
            "akka.cluster.seed-nodes" to getAllSeedNodes().map {
                formatSeedNode(
                    akkaSystemName,
                    it.host.address,
                    it.port
                )
            },
            "akka.cluster.auto-down-unreachable-after" to "off",
        )
        val centerConfig = ConfigFactory.parseMap(configs)
        val roleConfig = ConfigFactory.load("${node.role.name}.conf")
        return centerConfig.withFallback(roleConfig)
    }

    private fun getAllNodes(): Map<Host, List<Node>> {
        val allNodes = mutableMapOf<Host, MutableList<Node>>()
        with(zookeeperConfigCenterComponent) {
            val serverHosts = getConfigEx<ServerHosts>(ServerHosts.PATH)
            val nodes = mutableListOf<Node>()
            getChildren(serverHosts.path()).forEach { hostPath ->
                val hostFullPath = "${serverHosts.path()}/$hostPath"
                val host = getConfigEx<Host>(hostFullPath)
                getChildren(hostFullPath).forEach { nodePath ->
                    val nodeFullPath = "${hostFullPath}/$nodePath"
                    nodes.add(getConfigEx<Node>(nodeFullPath))
                }
                allNodes[host] = nodes
            }
        }
        return allNodes
    }

    private fun initSelfNode() {
        val path = "${Host(GlobalEnv.machineIp).path()}/${role.name.lowercase()}:${port}"
        selfNode = zookeeperConfigCenterComponent.getConfigEx(path)
    }

    private fun initSysName() {
        akkaSystemName = zookeeperConfigCenterComponent.getConfigEx<ServerHosts>(ServerHosts.PATH).systemName
    }

    private fun getAllSeedNodes() = getAllNodes().values.flatten().filter { it.seed }

    private fun formatSeedNode(systemName: String, host: String, port: Int) = "akka://$systemName@$host:$port"

    override fun shutdown() {

    }
}