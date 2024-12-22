package com.mikai233.common.core

import akka.actor.ActorSystem
import com.google.common.io.Resources
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.config.ZookeeperConfigMeta
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.typesafe.config.Config
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import com.mikai233.common.core.component.config.Config as ZookeeperConfig

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

    val zookeeperConfigMeta: ZookeeperConfigMeta by lazy {
        val configMetaFile = File(Resources.getResource("zookeeper.json").file)
        Json.fromBytes(configMetaFile.readBytes())
    }

    val zookeeperConfigMetaIndex: Map<KClass<out ZookeeperConfig>, ZookeeperConfigMeta> by lazy {
        val index = mutableMapOf<KClass<out ZookeeperConfig>, ZookeeperConfigMeta>()
        var current = zookeeperConfigMeta
        //TODO
        index
    }

    val configs: ConcurrentHashMap<KClass<out ZookeeperConfig>, ZookeeperConfig> = ConcurrentHashMap()

    @Volatile
    var state: State = State.Uninitialized
        set(value) {
            val previousState = field
            field = value
            logger.info("{} state change from:{} to:{}", this::class.simpleName, previousState, field)
            eventListeners[value]?.forEach { listener ->
                tryCatch(logger) { listener() }
            }
        }
    private val eventListeners: EnumMap<State, MutableList<() -> Unit>> = EnumMap(State::class.java)

    fun addListener(state: State, listener: () -> Unit) {
        eventListeners.computeIfAbsent(state) { mutableListOf() }.add(listener)
    }
}
