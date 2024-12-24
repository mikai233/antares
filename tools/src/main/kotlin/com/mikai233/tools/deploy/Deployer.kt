package com.mikai233.tools.deploy

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.config.Node
import com.mikai233.common.core.component.config.nodePath
import com.mikai233.common.core.component.config.serverHostPath
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.asyncZookeeperClient
import java.io.File

object Deployer {
    private val hostFullPath = serverHostPath(GlobalEnv.machineIp)
    private val curator = asyncZookeeperClient(GlobalEnv.zkConnect).apply {
        start()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        check(args.size == 1) { "please input antares version" }
        val deployDir = "deploy"
        File(deployDir).mkdir()
        with(curator) {
            children.forPath(hostFullPath).forEach {
                val dataBytes = data.forPath(nodePath(GlobalEnv.machineIp, it))
                val node = Json.fromBytes<Node>(dataBytes)
                File("$deployDir/${node.role}_${node.port}").createNewFile()
            }
        }
    }
}
