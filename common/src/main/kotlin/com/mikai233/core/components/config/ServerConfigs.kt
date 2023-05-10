package com.mikai233.core.components.config

import com.mikai233.core.Server
import com.mikai233.core.components.Component
import com.mikai233.ext.Json
import com.mikai233.ext.logger

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/11
 */
class ServerConfigs : Component {
    val logger = logger()
    var akkaConfig = AkkaConfig("127.0.0.1")
    lateinit var zookeeperConfigCenter: ZookeeperConfigCenter
    override fun init(server: Server) {
        zookeeperConfigCenter = server.component<ZookeeperConfigCenter>()
        zookeeperConfigCenter.watchConfig(akkaConfig.path()) { oldData, data ->
            val newConfig = Json.fromJson<AkkaConfig>(data.data)
            logger.info("config update {} => {}", akkaConfig, newConfig)
            akkaConfig = newConfig
        }
    }

    override fun shutdown(server: Server) {
        TODO("Not yet implemented")
    }
}