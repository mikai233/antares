package com.mikai233.tools.config

import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.extension.asyncZookeeperClient
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val zookeeperConnect = RuntimeEnv.fromSystem().zookeeperConnect
    LocalGameConfigPublisher.publish(
        ZookeeperConfigStore(asyncZookeeperClient(zookeeperConnect)),
    )
}
