package com.mikai233.stardust

import ch.qos.logback.classic.LoggerContext
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.GameNodeRuntime
import com.mikai233.common.core.Role
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.common.extension.logger
import com.mikai233.gate.GateNode
import com.mikai233.global.GlobalNode
import com.mikai233.gm.GmNode
import com.mikai233.player.PlayerNode
import com.mikai233.world.WorldNode
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.RuntimeNodeConfig
import io.github.realmlabs.asteria.config.center.JacksonConfigCodec
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.system.exitProcess

object StardustCluster {
    private val logger = logger()
    private val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    private val nodeByRole: EnumMap<Role, KClass<out GameNodeRuntime>> = EnumMap(Role::class.java)
    private val nodes: ArrayList<GameNodeRuntime> = arrayListOf()

    init {
        Role.entries.forEach {
            when (it) {
                Role.Player -> {
                    nodeByRole[it] = PlayerNode::class
                }

                Role.Gate -> {
                    nodeByRole[it] = GateNode::class
                }

                Role.World -> {
                    nodeByRole[it] = WorldNode::class
                }

                Role.Global -> {
                    nodeByRole[it] = GlobalNode::class
                }

                Role.Gm -> {
                    nodeByRole[it] = GmNode::class
                }
            }
        }
    }

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
                    val role = requireNotNull(nodeConfig.roles.firstNotNullOfOrNull(::roleOf)) {
                        "node ${nodeConfig.nodeId} has no known game role: ${nodeConfig.roles}"
                    }
                    val nodeClass = nodeByRole[role]
                    if (nodeClass != null) {
                        val constructor =
                            requireNotNull(nodeClass.primaryConstructor) { "$nodeClass primaryConstructor not found" }
                        val addr = InetSocketAddress(nodeConfig.host, nodeConfig.port)
                        val config = ConfigFactory.load("${role.name.lowercase()}.conf")
                        val sameJvm = true
                        val node = constructor.call(addr, systemName, nodeConfig.nodeId, config, zookeeperConnectString, sameJvm)
                        node.launch()
                        nodes.add(node)
                    } else {
                        logger.error("node of role:{} not register", role)
                    }
                }
            }
        }
    }

    private fun roleOf(role: String): Role? {
        return Role.entries.firstOrNull { it.name == role }
    }
}
