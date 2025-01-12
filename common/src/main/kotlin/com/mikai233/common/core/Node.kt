package com.mikai233.common.core

import akka.Done
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.mikai233.common.config.*
import com.mikai233.common.db.MongoDB
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.excel.*
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import com.mikai233.common.script.ScriptActor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier
import kotlin.random.Random
import kotlin.random.nextLong

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 * @param addr 节点地址
 * @param roles 节点角色
 * @param name 节点名称
 * @param config 节点配置
 * @param zookeeperConnectString zookeeper连接字符串
 */
open class Node(
    val addr: InetSocketAddress,
    val roles: List<Role>,
    val name: String,
    val config: Config,
    zookeeperConnectString: String,
    private val sameJvm: Boolean = false,
) {
    val logger = logger()

    lateinit var system: ActorSystem
        protected set

    lateinit var coroutineScope: CoroutineScope

    val zookeeper: AsyncCuratorFramework by lazy {
        val client = CuratorFrameworkFactory.newClient(
            zookeeperConnectString,
            ExponentialBackoffRetry(2000, 10, 60000)
        )
        client.start()
        AsyncCuratorFramework.wrap(client)
    }

    val mongoDB = MongoDB(zookeeper)

    private val gameWorldMetaCache = ConfigCache(zookeeper, GAME_WORLDS, GameWorldMeta::class)

    val gameWorldMeta get() = gameWorldMetaCache.config

    val gameWorldConfigCache = ConfigChildrenCache(zookeeper, GAME_WORLDS, GameWorldConfig::class)

    @Volatile
    lateinit var gameConfigManager: GameConfigManager
        private set

    @Volatile
    lateinit var gameConfigManagerHashcode: HashCode
        private set

    lateinit var scriptActor: ActorRef
        protected set

    @Volatile
    var state: State = State.Unstarted
        private set

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

    protected open suspend fun beforeStart() {}

    protected open suspend fun startSystem() {
        val remoteConfig = resolveRemoteConfig()
        val config = remoteConfig.withFallback(config)
        system = ActorSystem.create(name, config)
        coroutineScope = CoroutineScope(system.dispatcher.asCoroutineDispatcher() + SupervisorJob())
        addCoordinatedShutdownTasks()
        spawnScriptActor()
        resolveGameConfigManager()
        changeState(State.Starting)
    }

    protected open suspend fun afterStart() {
        changeState(State.Started)
    }

    private fun addCoordinatedShutdownTasks() {
        with(CoordinatedShutdown.get(system)) {
            addTask(CoordinatedShutdown.PhaseClusterLeave(), "leave_delay", taskSupplier {
                delay(Random.nextLong(1000L..5000L))
            })
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
            val nodePaths = zookeeper.children.forPath(SERVER_HOSTS).await().map { host ->
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
            "akka.cluster.roles" to roles.map { it.name },
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
        scriptActor = system.actorOf(ScriptActor.props(this), ScriptActor.NAME)
    }

    inline fun <reified T : V> getConfig(): T {
        return gameConfigManager.get<T>()
    }

    inline fun <reified T : GameConfigs<K, C>, C : GameConfig<K>, K : Any> getConfigById(id: K): C {
        return gameConfigManager.getById<T, _, _>(id)
    }

    private suspend fun resolveGameConfigManager() {
        //配置表哈希需要稳定的哈希函数
        fun calculateConfigHash(bytes: ByteArray) {
            gameConfigManagerHashcode = Hashing.murmur3_128(233).hashBytes(bytes)
        }

        val cache = CuratorCache.build(zookeeper.unwrap(), GAME_CONFIG, CuratorCache.Options.SINGLE_NODE_CACHE)
        addStateListener(State.Stopping) { cache.close() }
        val bytes = zookeeper.data.forPath(GAME_CONFIG).await()
        gameConfigManager = GameConfigManagerSerde.deserialize(bytes)
        calculateConfigHash(bytes)
        cache.listenable().addListener { type, oldData, data ->
            when (type) {
                CuratorCacheListener.Type.NODE_CREATED -> {
                    gameConfigManager = GameConfigManagerSerde.deserialize(data.data)
                    logger.info(
                        "{} created, version: {}",
                        GameConfigManager::class.simpleName,
                        gameConfigManager.version
                    )
                    calculateConfigHash(bytes)
                    system.eventStream.publish(GameConfigUpdateEvent)
                }

                CuratorCacheListener.Type.NODE_CHANGED -> {
                    gameConfigManager = GameConfigManagerSerde.deserialize(data.data)
                    logger.info(
                        "{} updated, version: {}",
                        GameConfigManager::class.simpleName,
                        gameConfigManager.version
                    )
                    calculateConfigHash(bytes)
                    system.eventStream.publish(GameConfigUpdateEvent)
                }

                CuratorCacheListener.Type.NODE_DELETED -> {
                    logger.warn("Node {} deleted:{}", GameConfigManager::class.simpleName, oldData.path)
                }

                null -> Unit
            }
        }
        cache.start()
    }
}