package com.mikai233.tools.zookeeper

import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.config.*
import com.mikai233.common.extension.asyncZookeeperClient
import io.github.realmlabs.asteria.config.center.JacksonConfigCodec
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import kotlinx.coroutines.runBlocking

/**
 * Initializes the runtime config keys needed by the Kubernetes deployment skeleton.
 *
 * Cluster topology is intentionally not written here: Kubernetes discovery owns cluster membership.
 */
fun main() = runBlocking {
    val runtimeEnv = RuntimeEnv.fromSystem()
    val client = asyncZookeeperClient(runtimeEnv.zookeeperConnect)
    val repository = RuntimeConfigRepository(
        ZookeeperConfigStore(client),
        JacksonConfigCodec(),
    )

    repository.put(
        DATA_SOURCE_GAME,
        DataSourceConfig(
            databaseName = env("MONGO_DATABASE", "asteria_example"),
            sources = listOf(
                DataSource(
                    host = env("MONGO_HOST", "mongodb"),
                    port = intEnv("MONGO_PORT", 27017),
                ),
            ),
        ),
    )

    repository.put(
        nettyConfigPath(env("GATE_NETTY_CONFIG_NODE_ID", "gate")),
        NettyConfig(
            host = env("GATE_HOST", "0.0.0.0"),
            port = intEnv("GATE_PORT", 6666),
        ),
    )

    val worldCount = intEnv("GAME_WORLD_COUNT", 1)
    val baseWorldId = longEnv("GAME_WORLD_BASE_ID", 16800L)
    repeat(worldCount) { index ->
        val world = generateGameWorld(baseWorldId + index)
        repository.put(GAME_WORLDS / world.id.toString(), world)
    }
}

private fun generateGameWorld(worldId: Long): GameWorldConfig {
    return GameWorldConfig(
        id = worldId,
        name = env("GAME_WORLD_NAME_PREFIX", "时光回忆") + ":$worldId",
        openDateTime = env("GAME_WORLD_OPEN_TIME", "2025-01-01T10:00:00"),
        onlineLimit = longEnv("GAME_WORLD_MAX_ONLINE", 5000),
        registerLimit = longEnv("GAME_WORLD_MAX_CREATE", 10000),
    )
}

private fun env(name: String, defaultValue: String): String {
    return System.getenv(name)?.takeIf(String::isNotBlank) ?: defaultValue
}

private fun intEnv(name: String, defaultValue: Int): Int {
    return env(name, defaultValue.toString()).toInt()
}

private fun longEnv(name: String, defaultValue: Long): Long {
    return env(name, defaultValue.toString()).toLong()
}
