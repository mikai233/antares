package com.mikai233.common.config

import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import kotlinx.coroutines.future.await
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.x.async.AsyncCuratorFramework
import kotlin.reflect.KClass

class ConfigCache<C>(
    private val zookeeper: AsyncCuratorFramework,
    val path: String,
    private val clazz: KClass<C>,
    onUpdated: ((C) -> Unit)? = null
) : AutoCloseable where C : Any {
    val logger = logger()

    @Volatile
    var config: C
        private set

    private val cache: CuratorCache

    init {
        val zookeeper = zookeeper.unwrap()
        val bytes = zookeeper.data.forPath(path)
        config = Json.fromBytes(bytes, clazz)
        cache = CuratorCache.build(zookeeper, path, CuratorCache.Options.SINGLE_NODE_CACHE)
        cache.listenable().addListener { type, _, data ->
            when (type) {
                CuratorCacheListener.Type.NODE_CREATED -> {
                    logger.info("Node {} created:{}", clazz, data.path)
                    config = Json.fromBytes(data.data, clazz)
                    onUpdated?.invoke(config)
                }

                CuratorCacheListener.Type.NODE_CHANGED -> {
                    logger.info("Node {} changed:{}", clazz, data.path)
                    config = Json.fromBytes(data.data, clazz)
                    onUpdated?.invoke(config)
                }

                CuratorCacheListener.Type.NODE_DELETED -> {
                    logger.warn("Node {} deleted:{}", clazz, data.path)
                }

                null -> Unit
            }
        }
        cache.start()
    }

    suspend fun update(newConfig: C) {
        val bytes = Json.toBytes(newConfig)
        zookeeper.setData().forPath(path, bytes).await()
    }

    override fun close() {
        cache.close()
    }
}

class ConfigChildrenCache<C>(
    zookeeper: AsyncCuratorFramework,
    val path: String,
    private val clazz: KClass<C>,
    onChanged: ((Map<String, C>) -> Unit)? = null
) : AutoCloseable, Map<String, C> where C : Any {
    private val configsByName: MutableMap<String, C> = mutableMapOf()

    private val cache: CuratorCache

    init {
        val client = zookeeper.unwrap()
        client.children.forPath(path).forEach { childPath ->
            val bytes = client.data.forPath("$path/$childPath")
            val child = Json.fromBytes(bytes, clazz)
            configsByName[childPath] = child
        }
        cache = CuratorCache.build(client, path)
        val regex = Regex("^$path/([^/]+)$")
        cache.listenable().addListener { type, _, data ->
            val matchResult = regex.matchEntire(data.path)
            if (matchResult != null) {
                val childPath = matchResult.groupValues[1]
                when (type) {
                    CuratorCacheListener.Type.NODE_CREATED,
                    CuratorCacheListener.Type.NODE_CHANGED -> {
                        val child = Json.fromBytes(data.data, clazz)
                        configsByName[childPath] = child
                        onChanged?.invoke(configsByName)
                    }

                    CuratorCacheListener.Type.NODE_DELETED -> {
                        configsByName.remove(childPath)
                        onChanged?.invoke(configsByName)
                    }

                    null -> Unit
                }
            }
        }
        cache.start()
    }

    override fun close() {
        cache.close()
    }

    override val size: Int
        get() = configsByName.size
    override val entries: Set<Map.Entry<String, C>>
        get() = configsByName.entries
    override val keys: Set<String>
        get() = configsByName.keys
    override val values: Collection<C>
        get() = configsByName.values

    override fun containsKey(key: String): Boolean {
        return configsByName.containsKey(key)
    }

    override fun containsValue(value: C): Boolean {
        return configsByName.containsValue(value)
    }

    override fun get(key: String): C? {
        return configsByName[key]
    }

    override fun isEmpty(): Boolean {
        return configsByName.isEmpty()
    }
}
