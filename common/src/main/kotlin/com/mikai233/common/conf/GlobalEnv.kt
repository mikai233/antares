package com.mikai233.common.conf

import com.mikai233.common.extension.getMachineIp
import com.mikai233.common.extension.getenv
import kotlinx.datetime.TimeZone


object GlobalEnv {
    val zkConnect: String = getenv("ZK_CONNECT") ?: "127.0.0.1:2181"

    val machineIp: String = getenv("MACHINE_IP") ?: getMachineIp()
    val serverMode = ServerMode.DevMode

    const val SYSTEM_NAME = com.mikai233.common.config.SYSTEM_NAME

    val zoneId = TimeZone.of("Asia/Shanghai")
}
