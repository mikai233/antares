package com.mikai233.stardust

import ch.qos.logback.classic.LoggerContext
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.config.NodeConfig
import com.mikai233.common.config.nodePath
import com.mikai233.common.config.serverHostsPath
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Role
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.common.extension.logger
import com.mikai233.gate.GateNode
import com.mikai233.global.GlobalNode
import com.mikai233.gm.GmNode
import com.mikai233.player.PlayerNode
import com.mikai233.world.WorldNode
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.system.exitProcess

object StardustLauncher {
    private val logger = logger()
    private val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    private val nodeByRole: EnumMap<Role, KClass<out Launcher>> = EnumMap(Role::class.java)
    private val nodes: ArrayList<Launcher> = arrayListOf()

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
        }.sortedByDescending { it.seed }
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
    }
}
