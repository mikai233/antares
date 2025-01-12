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
import java.io.FileOutputStream

class Cli {
    @Parameter(names = ["-e", "--excel"], description = "excel path", required = true)
    lateinit var excelPath: String

    @Parameter(names = ["-v", "--version"], description = "game version", required = true)
    lateinit var version: String

    @Parameter(names = ["-f", "--file"], description = "generate file or upload to zookeeper")
    var file: Boolean = false
}

suspend fun main(args: Array<String>) {
    val cli = Cli()
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val client = asyncZookeeperClient(GlobalEnv.zkConnect)
    val manager = createGameConfigManager(
        cli.excelPath,
        cli.version,
    )
    if (cli.file) {
        withContext(Dispatchers.IO) {
            GameConfigManagerSerde.serialize(
                manager,
                FileOutputStream("game_configs_bin.tar.gz")
            )
        }
    } else {
        val bytes = GameConfigManagerSerde.serialize(manager)
        if (client.checkExists().forPath(com.mikai233.common.config.GAME_CONFIG).await() == null) {
            client.create().forPath(com.mikai233.common.config.GAME_CONFIG, bytes).await()
        } else {
            client.setData().forPath(com.mikai233.common.config.GAME_CONFIG, bytes).await()
        }
    }
}