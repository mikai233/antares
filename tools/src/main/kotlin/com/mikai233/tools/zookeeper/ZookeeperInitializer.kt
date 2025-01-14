package com.mikai233.tools.zookeeper

import com.google.common.io.Resources
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.config.GameWorldMeta
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.asyncZookeeperClient
import kotlinx.coroutines.future.await
import org.apache.curator.x.async.AsyncCuratorFramework
import org.apache.curator.x.async.api.CreateOption
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
suspend fun main() {
    val logger = LoggerFactory.getLogger("ZookeeperInitializerKt")
    val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    val data = Json.fromBytes<NodeData>(File(Resources.getResource("zookeeper.json").file).readBytes())
    with(client) {
        setData(null, data, logger)
    }
    val gameWorldConfigs = mutableListOf<GameWorldConfig>()
    repeat(1000) {
        gameWorldConfigs.add(generateGameWorld(16800L + it))
    }
    val nodeData = NodeData(
        "game_worlds",
        GameWorldMeta(gameWorldConfigs.map { it.id }.toSet()),
        gameWorldConfigs.map {
            NodeData(
                "${it.id}",
                it,
                null
            )
        }
    )
    with(client) {
        setData("/${data.name}", nodeData, logger)
    }
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

private suspend fun AsyncCuratorFramework.setData(parent: String?, nodeData: NodeData, logger: Logger) {
    val path = "${parent ?: ""}/${nodeData.name}"
    val data = nodeData.data
    if (data == null) {
        create().withOptions(setOf(CreateOption.setDataIfExists)).forPath(path).await()
    } else {
        create().withOptions(setOf(CreateOption.setDataIfExists)).forPath(path, Json.toBytes(data)).await()
        logger.info("set {} {}", path, data)
    }
    nodeData.children?.forEach { childData ->
        setData(path, childData, logger)
    }
}