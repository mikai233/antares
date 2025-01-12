package com.mikai233.tools.deploy

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.config.serverHostsPath
import com.mikai233.common.extension.asyncZookeeperClient
import java.io.File

object Deployer {
    private val hostPath = serverHostsPath(GlobalEnv.machineIp)
    private val client = asyncZookeeperClient(GlobalEnv.zkConnect)

    @JvmStatic
    fun main(args: Array<String>) {
        check(args.size == 1) { "please input antares version" }
        val deployDir = "deploy"
        File(deployDir).mkdir()
//        with(client) {
//            children.forPath(hostPath).forEach {
//                val dataBytes = data.forPath(nodePath(GlobalEnv.machineIp, it))
//                val node = Json.fromBytes<Node>(dataBytes)
//                File("$deployDir/${node.role}_${node.port}").createNewFile()
//            }
//        }
    }
}
