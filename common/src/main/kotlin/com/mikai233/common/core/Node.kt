package com.mikai233.common.core

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import com.google.common.io.Resources
import com.mikai233.common.core.config.ConfigMeta
import com.mikai233.common.core.config.TestConfig
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.typesafe.config.Config
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.reflect.KClass
import com.mikai233.common.core.config.Config as ZkConfig

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 */
open class Node(val role: Role, val name: String, val config: Config, zookeeperConnectString: String) {
    val logger = logger()

    val system: ActorSystem = ActorSystem.create(name, config)

    val zookeeper: AsyncCuratorFramework by lazy {
        val client = CuratorFrameworkFactory.newClient(
            zookeeperConnectString,
            ExponentialBackoffRetry(2000, 10, 60000)
        )
        client.start()
        AsyncCuratorFramework.wrap(client)
    }

    val configMeta: ConfigMeta by lazy {
        val configMetaFile = File(Resources.getResource("zookeeper.json").file)
        Json.fromBytes(configMetaFile.readBytes())
    }

    val configMetaIndex: Map<KClass<*>, List<ConfigMeta>> by lazy {
        val index = mutableMapOf<KClass<*>, MutableList<ConfigMeta>>()
        fun traverse(meta: ConfigMeta) {
            index.computeIfAbsent(meta.clazz) { mutableListOf() }.add(meta)
            meta.children.values.forEach { traverse(it) }
        }
        traverse(configMeta)
        index
    }

    val configs: ConcurrentHashMap<KClass<*>, List<ZkConfig>> = ConcurrentHashMap()

    @Volatile
    private var state: State = State.Unstarted

    suspend fun changeState(newState: State) {
        val previousState = state
        state = newState
        logger.info("{} state change from:{} to:{}", this::class.simpleName, previousState, newState)
        stateListeners[newState]?.forEach { listener ->
            listener()
        }
    }

    private val stateListeners: EnumMap<State, MutableList<suspend () -> Unit>> = EnumMap(State::class.java)

    private val configListeners: MutableMap<String, MutableList<() -> Unit>> = mutableMapOf()

    fun addStateListener(state: State, listener: suspend () -> Unit) {
        stateListeners.computeIfAbsent(state) { mutableListOf() }.add(listener)
    }

    internal inline fun <reified C> addConfigListener(path: String? = null, noinline block: () -> Unit) where C : Any {
        val metas = requireNotNull(configMetaIndex[C::class]) { "No config meta found for ${C::class}" }
        if (metas.size > 1) {
            val path = requireNotNull(path) { "Multiple config metas found for ${C::class}, please specify path" }
            val meta =
                requireNotNull(metas.firstOrNull { it.name == path }) { "No config meta found for ${C::class} with path $path" }
            configListeners.computeIfAbsent(meta.name) { mutableListOf() }.add(block)
        } else {

        }
        configListeners.computeIfAbsent(C::class) { mutableListOf() }.add(block)
    }

    /**
     * 获取zookeeper指定类型的配置，如果这个类型的配置在zookeeper的多个地方都有，那么抛出错误，需要使用[path]参数指定路径
     */
    inline fun <reified C> getConfig(path: String? = null): C where C : Any? {
        val configs = requireNotNull(configs[C::class]) { "No config found for ${C::class}" }
        if (configs.size > 1) {
            val path = requireNotNull(path) { "Multiple configs found for ${C::class}, please specify path" }
            return configs.first { it.path == path }.data as C
        } else {
            return configs.first().data as C
        }
    }

    /**
     * 获取zookeeper指定类型的配置列表，只能是zookeeper的某个节点下的所有直接子节点配置
     * 如果其它zookeeper的其它地方也有相同配置，那么抛出错误，需要使用[path]参数指定路径
     */
    inline fun <reified C> getConfigs(path: String? = null): List<C> where C : Any? {
        val configs = configs[C::class] ?: emptyList()
        val size = configs.map { it.path }.toSet().size
        if (size > 1) {
            val path = requireNotNull(path) { "Multiple configs found for ${C::class}, please specify path" }
            return configs.filter { it.path == path }.map { it.data as C }
        } else {
            return configs.map { it.data as C }
        }
    }

    suspend fun start() {
        addCoordinatedShutdownTasks()
        initConfigs()
        changeState(State.Starting)
    }

    private suspend fun initConfigs() {
        configMetaIndex.forEach { (clazz, meta) ->
            val data = zookeeper.data.forPath(meta.name).await()
            val config = Json.fromBytes(data, clazz)
            configs[clazz] = config
            val cache = CuratorCache.build(zookeeper.unwrap(), meta.name)
            cache.listenable().addListener { type, oldData, data ->
                when (type) {
                    CuratorCacheListener.Type.NODE_CREATED -> {
                        logger.info("Node {} created:{}", clazz, data.path)
                        val newConfig = Json.fromBytes(data.data, clazz)
                        configs[clazz] = newConfig
                        configListeners[clazz]?.forEach { tryCatch(logger) { it() } }
                    }

                    CuratorCacheListener.Type.NODE_CHANGED -> {
                        logger.info("Node {} changed:{}", clazz, data.path)
                        val newConfig = Json.fromBytes(data.data, clazz)
                        configs[clazz] = newConfig
                        configListeners[clazz]?.forEach { tryCatch(logger) { it() } }
                    }

                    CuratorCacheListener.Type.NODE_DELETED -> {
                        logger.warn("Node {} deleted:{}", clazz, oldData.path)
                    }
                }
            }
            cache.start()
        }
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

    fun taskSupplier(task: suspend () -> Unit): Supplier<CompletionStage<Done>> {
        return Supplier {
            CompletableFuture.supplyAsync {
                runBlocking { task.invoke() }
                Done.done()
            }
        }
    }
}
