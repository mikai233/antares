package com.mikai233.tools.config

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.extension.asyncZookeeperClient
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val zookeeperConnect = System.getenv("ZK_CONNECT")?.takeIf(String::isNotBlank) ?: GlobalEnv.zkConnect
    LocalGameConfigPublisher.publish(
        ZookeeperConfigStore(asyncZookeeperClient(zookeeperConnect)),
        GameConfigPublishOptions.fromEnvironment(),
    )
}
