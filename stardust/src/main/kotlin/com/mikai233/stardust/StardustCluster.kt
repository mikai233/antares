package com.mikai233.stardust

import ch.qos.logback.classic.LoggerContext
import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.config.SYSTEM_NAME
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.common.extension.logger
import com.mikai233.common.runtime.GameRoles
import com.mikai233.common.runtime.LaunchableNode
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
import kotlinx.coroutines.future.await
import org.apache.pekko.actor.ActorSystem
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
        runtimeEnv: RuntimeEnv,
    ) -> LaunchableNode

    private val logger = logger()
    private val nodeByRole: Map<String, NodeFactory> = mapOf(
        GameRoles.Player to { addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv ->
            PlayerNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv)
        },
        GameRoles.Gate to { addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv ->
            GateNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv)
        },
        GameRoles.World to { addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv ->
            WorldNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv)
        },
        GameRoles.Global to { addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv ->
            GlobalNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv)
        },
        GameRoles.Gm to { addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv ->
            GmNode(addr, name, nodeId, config, zookeeperConnectString, sameJvm, runtimeEnv)
        },
    )

    suspend fun launch() {
        val runtimeEnv = RuntimeEnv.fromSystem()
        val repository = RuntimeConfigRepository(
            ZookeeperConfigStore(asyncZookeeperClient(runtimeEnv.zookeeperConnect)),
            JacksonConfigCodec(),
        )
        val layout = ClusterConfigLayout.default(SYSTEM_NAME)
        val nodeConfigs = repository.children<RuntimeNodeConfig>(layout.nodes)
            .values
            .values
            .map { it.value }
            .sortedByDescending { it.seed }
        check(nodeConfigs.isNotEmpty()) {
            "no runtime node configs found under ${layout.nodes}; run tools.zookeeper.ZookeeperInitializer first"
        }

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.error("failed to launch development cluster", throwable)
            (LoggerFactory.getILoggerFactory() as LoggerContext).stop()
            exitProcess(-1)
        }

        val nodes: List<LaunchableNode> = supervisorScope {
            nodeConfigs.map { nodeConfig ->
                async(exceptionHandler) {
                    logger.info("launch development node: {}", nodeConfig)
                    val role = requireNotNull(nodeConfig.roles.firstOrNull(nodeByRole::containsKey)) {
                        "node ${nodeConfig.nodeId} has no known game role: ${nodeConfig.roles}"
                    }
                    val nodeFactory = requireNotNull(nodeByRole[role]) { "node factory missing for role: $role" }
                    val addr = InetSocketAddress(nodeConfig.host, nodeConfig.port)
                    val config = ConfigFactory.load("${role.lowercase()}.conf")
                    nodeFactory(
                        addr,
                        SYSTEM_NAME,
                        nodeConfig.nodeId,
                        config,
                        runtimeEnv.zookeeperConnect,
                        true,
                        runtimeEnv,
                    ).also { it.launch() }
                }
            }.awaitAll()
        }
        supervisorScope {
            nodes.forEach { node ->
                launch(exceptionHandler) {
                    node.services.get(ActorSystem::class).whenTerminated.await()
                }
            }
        }
    }
}
