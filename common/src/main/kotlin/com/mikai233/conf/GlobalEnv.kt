package com.mikai233.conf

import com.mikai233.ext.getenv

object GlobalEnv {
    val zkConnect: String = getenv("ZK_CONNECT") ?: "127.0.0.1"

    //    val machineIp: String = getenv("MACHINE_IP") ?: getMachineIp()
    val machineIp: String = "127.0.0.1"
}