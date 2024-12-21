package com.mikai233.common.core.component

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.config.Config
import com.mikai233.common.core.component.config.ConfigCenter
import com.mikai233.common.extension.Json
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.recipes.cache.ChildData
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework

class ZookeeperConfigCenter(connectionString: String = GlobalEnv.zkConnect) : ConfigCenter, AutoCloseable {
    private val client: CuratorFramework
    private val asyncClient: AsyncCuratorFramework
    private val cacheMap: HashMap<String, CuratorCache> = hashMapOf()

    init {
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

    override fun addConfig(config: Config) {
        val path = config.path()
        val value = Json.toBytes(config, true)
        if (exists(path).not()) {
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

    override fun watchConfig(path: String, onUpdate: (ByteArray) -> Unit) {
        val cache = CuratorCache.builder(client, path).build().apply {
            listenable().addListener(
                CuratorCacheListener.builder().forCreatesAndChanges { _: ChildData?, node: ChildData? ->
                    if (node?.path == path) {
                        onUpdate(node.data)
                    }
                }.build()
            )
            start()
        }
        cacheMap.remove(path)?.close()
        cacheMap[path] = cache
    }

    override fun getChildren(path: String): List<String> {
        return client.children.forPath(path)
    }

    override fun exists(path: String): Boolean {
        return client.checkExists().forPath(path) != null
    }

    override fun close() {
        client.close()
    }
}
