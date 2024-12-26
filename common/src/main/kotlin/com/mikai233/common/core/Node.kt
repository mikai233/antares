package com.mikai233.common.core

import akka.Done
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import com.mikai233.common.core.config.NodeConfig
import com.mikai233.common.core.config.ServerHosts
import com.mikai233.common.core.config.nodePath
import com.mikai233.common.core.config.serverHostsPath
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import com.mikai233.common.script.ScriptActor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 * @param addr 节点地址
 * @param role 节点角色
 * @param name 节点名称
 * @param config 节点配置
 * @param zookeeperConnectString zookeeper连接字符串
 */
open class Node(
    private val addr: InetSocketAddress,
    val role: Role,
    val name: String,
    val config: Config,
    zookeeperConnectString: String,
    private val sameJvm: Boolean = false,
) {
    val logger = logger()

    lateinit var system: ActorSystem
        protected set

    private val zookeeper: AsyncCuratorFramework by lazy {
        val client = CuratorFrameworkFactory.newClient(
            zookeeperConnectString,
            ExponentialBackoffRetry(2000, 10, 60000)
        )
        client.start()
        AsyncCuratorFramework.wrap(client)
    }

    lateinit var scriptActor: ActorRef
        protected set

    @Volatile
    private var state: State = State.Unstarted

    protected open suspend fun changeState(newState: State) {
        val previousState = state
        state = newState
        logger.info("{} state change from:{} to:{}", this::class.simpleName, previousState, newState)
        stateListeners[newState]?.forEach { listener ->
            listener()
        }
    }

    private val stateListeners: EnumMap<State, MutableList<suspend () -> Unit>> = EnumMap(State::class.java)

    fun addStateListener(state: State, listener: suspend () -> Unit) {
        stateListeners.computeIfAbsent(state) { mutableListOf() }.add(listener)
    }

    protected open suspend fun start() {
        beforeStart()
        startSystem()
        afterStart()
    }

    protected open suspend fun beforeStart() {
    }

    protected open suspend fun startSystem() {
        val remoteConfig = resolveRemoteConfig()
        val config = remoteConfig.withFallback(config)
        system = ActorSystem.create(name, config)
        addCoordinatedShutdownTasks()
        spawnScriptActor()
        changeState(State.Starting)
    }

    protected open suspend fun afterStart() {
        changeState(State.Started)
    }

    private fun addCoordinatedShutdownTasks() {
        with(CoordinatedShutdown.get(system)) {
            addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "change_state_stopping", taskSupplier {
                changeState(State.Stopping)
            })
            addTask(CoordinatedShutdown.PhaseActorSystemTerminate(), "change_state_stopped", taskSupplier {
                changeState(State.Stopped)
            })
        }
    }

    protected open fun taskSupplier(task: suspend () -> Unit): Supplier<CompletionStage<Done>> {
        return Supplier {
            CompletableFuture.supplyAsync {
                runBlocking { task.invoke() }
                Done.done()
            }
        }
    }

    private fun formatSeedNode(systemName: String, host: String, port: Int) = "akka://$systemName@$host:$port"

    /**
     * 获取zookeeper中整个集群的种子节点配置
     */
    protected open suspend fun resolveRemoteConfig(): Config {
        val nodeConfigs = coroutineScope {
            val nodePaths = zookeeper.children.forPath(ServerHosts).await().map { host ->
                val hostPath = serverHostsPath(host)
                async {
                    val nodeNames = zookeeper.children.forPath(hostPath).await()
                    nodeNames.map { host to nodePath(host, it) }
                }
            }.awaitAll().flatten()
            nodePaths.map { (host, path) ->
                async {
                    val data = zookeeper.data.forPath(path).await()
                    host to Json.fromBytes<NodeConfig>(data)
                }
            }.awaitAll()
        }
        val seedNodeConfigs = nodeConfigs.filter { (_, config) -> config.seed }
        val seedNodes = seedNodeConfigs.map { (host, config) -> formatSeedNode(name, host, config.port) }

        val configs = mutableMapOf(
            "akka.cluster.roles" to listOf(role.name),
            "akka.remote.artery.canonical.hostname" to addr.hostString,
            "akka.remote.artery.canonical.port" to addr.port,
            "akka.cluster.seed-nodes" to seedNodes,
            "akka.cluster.auto-down-unreachable-after" to "off",
        )
        if (sameJvm) {
            configs["akka.cluster.jmx.multi-mbeans-in-same-jvm"] = "on"
        }
        return ConfigFactory.parseMap(configs)
    }

    private fun spawnScriptActor() {
        scriptActor = system.actorOf(ScriptActor.props(this), ScriptActor.name())
    }
}