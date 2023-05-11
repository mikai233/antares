package com.mikai233.core.components.config

import com.mikai233.core.Server
import com.mikai233.core.components.Component
import com.mikai233.ext.Json
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.recipes.cache.ChildData
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework


class ZookeeperConfigCenterComponent(private val connectionString: String = "localhost:2181") : Component, ConfigCenter {
    lateinit var client: CuratorFramework
        private set

    lateinit var asyncClient: AsyncCuratorFramework
        private set

    private val cacheMap: HashMap<String, CuratorCache> = hashMapOf()

    override fun addConfig(config: Config) {
        val path = config.path()
        val value = Json.toJsonBytes(config, true)
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path, value)
        } else {
            client.setData().forPath(path, value)
        }
    }

    override fun getConfig(path: String): ByteArray {
        return client.data.forPath(path)
    }

    override fun deleteConfig(path: String) {
        client.delete().forPath(path)
    }

    override fun watchConfig(path: String, onUpdate: (ChildData, ChildData) -> Unit) {
        val cache = CuratorCache.builder(client, path).build().apply {
            listenable().addListener(CuratorCacheListener { type, oldData, data ->
                when (type) {
                    CuratorCacheListener.Type.NODE_CREATED,
                    CuratorCacheListener.Type.NODE_CHANGED -> {
                        onUpdate(oldData, data)
                    }

                    else -> Unit
                }
            })
            start()
        }
        cacheMap.remove(path)?.close()
        cacheMap[path] = cache
    }

    override fun getChildren(path: String): List<String> {
        return client.children.forPath(path)
    }

    override fun init(server: Server) {
        val retryPolicy = ExponentialBackoffRetry(1000, 3)
        client = CuratorFrameworkFactory.builder()
            .connectString(connectionString)
            .sessionTimeoutMs(5000)
            .connectionTimeoutMs(5000)
            .retryPolicy(retryPolicy)
            .build()
        client.start()
        asyncClient = AsyncCuratorFramework.wrap(client)
    }

    override fun shutdown() {
        cacheMap.values.forEach(CuratorCache::close)
        if (this::client.isInitialized) {
            client.close()
        }
    }
}