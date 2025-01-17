package com.mikai233.tools.excel

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.excel.GameConfigManagerSerde
import com.mikai233.common.excel.createGameConfigManager
import com.mikai233.common.extension.asyncZookeeperClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.curator.x.async.api.CreateOption
import java.io.FileOutputStream

private class ExporterCli {
    @Parameter(names = ["-e", "--excel"], description = "excel path", required = true)
    lateinit var excelPath: String

    @Parameter(names = ["-v", "--version"], description = "game version", required = true)
    lateinit var version: String

    @Parameter(names = ["-f", "--file"], description = "generate file or upload to zookeeper")
    var file: Boolean = false
}

suspend fun main(args: Array<String>) {
    val exporterCli = ExporterCli()
    JCommander.newBuilder()
        .addObject(exporterCli)
        .build()
        .parse(*args)
    val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    val manager = createGameConfigManager(
        exporterCli.excelPath,
        exporterCli.version,
    )
    if (exporterCli.file) {
        withContext(Dispatchers.IO) {
            GameConfigManagerSerde.serialize(
                manager,
                FileOutputStream("game_configs_bin.tar.gz")
            )
        }
    } else {
        val bytes = GameConfigManagerSerde.serialize(manager)
        client.create()
            .withOptions(setOf(CreateOption.setDataIfExists, CreateOption.createParentsIfNeeded))
            .forPath(com.mikai233.common.config.GAME_CONFIG, bytes)
            .await()
    }
}