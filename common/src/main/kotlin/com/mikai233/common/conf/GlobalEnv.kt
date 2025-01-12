package com.mikai233.common.conf

import com.mikai233.common.extension.getenv


object GlobalEnv {
    val zkConnect: String = getenv("ZK_CONNECT") ?: "127.0.0.1:2181"

    //    val machineIp: String = getenv("MACHINE_IP") ?: getMachineIp()
    val machineIp: String = "127.0.0.1"
    val serverMode = ServerMode.DevMode

    const val LOGIN_PORT = 6666

    const val SYSTEM_NAME = com.mikai233.common.config.SYSTEM_NAME
}
