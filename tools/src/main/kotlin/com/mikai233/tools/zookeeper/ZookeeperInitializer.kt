package com.mikai233.tools.zookeeper

import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.config.*
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.tools.config.GameConfigPublishOptions
import com.mikai233.tools.config.LocalGameConfigPublisher
import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.ClusterTopology
import io.github.realmlabs.asteria.cluster.config.RuntimeNodeConfig
import io.github.realmlabs.asteria.config.center.JacksonConfigCodec
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import kotlinx.coroutines.runBlocking

/**
 * Initializes the local development config center using Asteria's runtime config model.
 */
fun main() = runBlocking {
    val runtimeEnv = RuntimeEnv.fromSystem()
    val client = asyncZookeeperClient(runtimeEnv.zookeeperConnect)
    val store = ZookeeperConfigStore(client)
    val codec = JacksonConfigCodec()
    val repository = RuntimeConfigRepository(store, codec)

    val topology = ClusterTopology(
        listOf(
            RuntimeNodeConfig("player-2333", "127.0.0.1", 2333, setOf("Player")),
            RuntimeNodeConfig("player-2334", "127.0.0.1", 2334, setOf("Player")),
            RuntimeNodeConfig("world-2335", "127.0.0.1", 2335, setOf("World")),
            RuntimeNodeConfig("global-2336", "127.0.0.1", 2336, setOf("Global"), seed = true),
            RuntimeNodeConfig("gate-2337", "127.0.0.1", 2337, setOf("Gate")),
            RuntimeNodeConfig("gm-2338", "127.0.0.1", 2338, setOf("Gm"), seed = true),
        ),
    )
    val clusterLayout = ClusterConfigLayout.default(SYSTEM_NAME)
    topology.nodes.forEach { node ->
        repository.put(clusterLayout.node(node.nodeId), node)
    }

    repository.put(
        DATA_SOURCE_GAME,
        DataSourceConfig(
            databaseName = "asteria_example",
            sources = listOf(DataSource("127.0.0.1", 27017)),
        ),
    )
    repository.put(nettyConfigPath("gate-2337"), NettyConfig(host = "0.0.0.0", port = 6666))

    repeat(1000) {
        val world = generateGameWorld(16800L + it)
        repository.put(GAME_WORLDS / world.id.toString(), world)
    }

    LocalGameConfigPublisher.publish(store, GameConfigPublishOptions.fromEnvironment())
}

private fun generateGameWorld(worldId: Long): GameWorldConfig {
    return GameWorldConfig(
        worldId,
        "时光回忆:$worldId",
        "2025-01-01T10:00:00",
        5000,
        10000,
    )
}
