package com.mikai233.stardust

import ch.qos.logback.classic.LoggerContext
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.GameRoles
import com.mikai233.common.core.LaunchableNode
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.common.extension.logger
import com.mikai233.gate.GateNode
import com.mikai233.global.GlobalNode
import com.mikai233.gm.GmNode
import com.mikai233.player.PlayerNode
import com.mikai233.world.WorldNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.RuntimeNodeConfig
import io.github.realmlabs.asteria.config.center.JacksonConfigCodec
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import kotlin.system.exitProcess

object StardustCluster {
    private typealias NodeFactory = (
        addr: InetSocketAddress,
        name: String,
        nodeId: String,
        config: Config,
        zookeeperConnectString: String,
        sameJvm: Boolean,
    ) -> LaunchableNode

    private val logger = logger()
    private val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    private val nodeByRole: Map<String, NodeFactory> = mapOf(
        GameRoles.Player to { addr, name, nodeId, config, zookeeperConnectString, sameJvm ->
            PlayerNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm)
        },
        GameRoles.Gate to { addr, name, nodeId, config, zookeeperConnectString, sameJvm ->
            GateNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm)
        },
        GameRoles.World to { addr, name, nodeId, config, zookeeperConnectString, sameJvm ->
            WorldNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm)
        },
        GameRoles.Global to { addr, name, nodeId, config, zookeeperConnectString, sameJvm ->
            GlobalNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm)
        },
        GameRoles.Gm to { addr, name, nodeId, config, zookeeperConnectString, sameJvm ->
            GmNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm)
        },
    )
    private val nodes: ArrayList<LaunchableNode> = arrayListOf()

    suspend fun launch() {
        val repository = RuntimeConfigRepository(ZookeeperConfigStore(client), JacksonConfigCodec())
        val layout = ClusterConfigLayout.default(GlobalEnv.SYSTEM_NAME)
        val nodeConfigs = repository.children<RuntimeNodeConfig>(layout.nodes)
            .values
            .values
            .map { it.value }
            .sortedByDescending { it.seed }
        val zookeeperConnectString = GlobalEnv.zkConnect
        val systemName = GlobalEnv.SYSTEM_NAME
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.error("launch node error", throwable)
            // 确保日志打印完成
            val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            loggerContext.stop()
            exitProcess(-1)
        }
        supervisorScope {
            nodeConfigs.forEach { nodeConfig ->
                launch(exceptionHandler) {
                    logger.info("launch node with config:{}", nodeConfig)
                    val role = requireNotNull(nodeConfig.roles.firstOrNull(nodeByRole::containsKey)) {
                        "node ${nodeConfig.nodeId} has no known game role: ${nodeConfig.roles}"
                    }
                    val nodeFactory = nodeByRole[role]
                    if (nodeFactory != null) {
                        val addr = InetSocketAddress(nodeConfig.host, nodeConfig.port)
                        val config = ConfigFactory.load("${role.lowercase()}.conf")
                        val sameJvm = true
                        val node = nodeFactory(addr, systemName, nodeConfig.nodeId, config, zookeeperConnectString, sameJvm)
                        node.launch()
                        nodes.add(node)
                    } else {
                        logger.error("node of role:{} not register", role)
                    }
                }
            }
        }
    }

}
