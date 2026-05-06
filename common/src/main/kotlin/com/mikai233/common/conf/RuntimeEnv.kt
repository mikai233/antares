package com.mikai233.common.conf

import com.mikai233.common.extension.getMachineIp
import kotlinx.datetime.TimeZone

data class RuntimeEnv(
    val zookeeperConnect: String,
    val machineIp: String,
    val serverMode: ServerMode,
    val zoneId: TimeZone,
) {
    companion object {
        fun fromSystem(): RuntimeEnv {
            return RuntimeEnv(
                zookeeperConnect = env("ZOOKEEPER_CONNECT", "127.0.0.1:2181"),
                machineIp = env("MACHINE_IP") ?: getMachineIp(),
                serverMode = env("SERVER_MODE", "dev").toServerMode(),
                zoneId = TimeZone.of(env("TIME_ZONE", "Asia/Shanghai")),
            )
        }

        private fun env(name: String): String? {
            return System.getenv(name)?.takeIf(String::isNotBlank)
        }

        private fun env(name: String, defaultValue: String): String {
            return env(name) ?: defaultValue
        }
    }
}

private fun String.toServerMode(): ServerMode {
    return when (lowercase().replace("_", "").replace("-", "")) {
        "dev", "devmode" -> ServerMode.DevMode
        "release", "releasemode", "prod", "production" -> ServerMode.ReleaseMode
        else -> error("unsupported SERVER_MODE=$this")
    }
}
