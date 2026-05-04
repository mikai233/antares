package com.mikai233.tools.zookeeper

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.config.DATA_SOURCE_GAME
import com.mikai233.common.config.DataSource
import com.mikai233.common.config.DataSourceConfig
import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.common.config.GAME_WORLDS
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.config.NettyConfig
import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.config.luban.GameTablesSnapshotBridge
import com.mikai233.common.config.nettyConfigPath
import com.mikai233.common.extension.asyncZookeeperClient
import com.mikai233.tools.config.LubanPublishBundleArtifacts
import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.ClusterTopology
import io.github.realmlabs.asteria.cluster.config.RuntimeNodeConfig
import io.github.realmlabs.asteria.config.center.JacksonConfigCodec
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import io.github.realmlabs.asteria.config.publisher.ConfigArtifactSource
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import io.github.realmlabs.asteria.config.publisher.ConfigPublisher
import kotlinx.coroutines.runBlocking

/**
 * Initializes the local development config center using Asteria's runtime config model.
 */
fun main() = runBlocking {
    val client = asyncZookeeperClient(GlobalEnv.zkConnect)
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
    val clusterLayout = ClusterConfigLayout.default(GlobalEnv.SYSTEM_NAME)
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

    publishDemoGameConfig(store)
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

private suspend fun publishDemoGameConfig(store: ZookeeperConfigStore) {
    val layout = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION)
    ConfigPublisher(
        loader = GameConfigSnapshotLoader(
            LubanBinaryConfigLoader(
                tablesType = GameTables::class,
                dataSource = MemoryLubanDataSource(
                    LubanPublishBundleArtifacts.unpackBundle(LubanPublishBundleArtifacts.bundleBytes()),
                ),
                bridge = GameTablesSnapshotBridge,
            ),
        ),
        artifactSource = { listOf(LubanPublishBundleArtifacts.bundleArtifact()) },
        store = store,
        layout = layout,
    ).publish()
}
