package com.mikai233.common.core.config

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
    val clazz: KClass<C>,
    onUpdate: ((C) -> Unit)? = null
) : AutoCloseable where C : Any {
    val logger = logger()

    @Volatile
    var config: C
        private set

    private val cache: CuratorCache

    init {
        val bytes = zookeeper.unwrap().data.forPath(path)
        config = Json.fromBytes(bytes, clazz)
        cache = CuratorCache.build(zookeeper.unwrap(), path)
        cache.listenable().addListener { type, oldData, data ->
            when (type) {
                CuratorCacheListener.Type.NODE_CREATED -> {
                    logger.info("Node {} created:{}", clazz, data.path)
                    config = Json.fromBytes(data.data, clazz)
                    onUpdate?.invoke(config)
                }

                CuratorCacheListener.Type.NODE_CHANGED -> {
                    logger.info("Node {} changed:{}", clazz, data.path)
                    config = Json.fromBytes(data.data, clazz)
                    onUpdate?.invoke(config)
                }

                CuratorCacheListener.Type.NODE_DELETED -> {
                    logger.warn("Node {} deleted:{}", clazz, oldData.path)
                }

                null -> Unit
            }
        }
        cache.start()
    }

    suspend fun update(newConfig: C) {
        val bytes = Json.toBytes(newConfig, clazz)
        zookeeper.setData().forPath(path, bytes).await()
    }

    override fun close() {
        cache.close()
    }
}