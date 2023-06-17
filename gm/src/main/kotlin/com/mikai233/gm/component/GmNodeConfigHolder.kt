package com.mikai233.gm.component

import com.mikai233.common.core.component.NodeConfigHolder
import com.mikai233.common.core.component.Role
import com.mikai233.common.inject.XKoin
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/6/18
 */
class GmNodeConfigHolder(
    private val koin: XKoin,
    private val role: Role,
    private val port: Int,
    private val sameJvm: Boolean,
) : NodeConfigHolder(koin, role, port, sameJvm) {
    override fun retrieveAkkaConfig(): Config {
        val config = super.retrieveAkkaConfig()
        val managementPort = "${port + 1000}"
        val extraConfig = mutableMapOf(
            "akka.management.http.port" to managementPort,
            "akka.management.http.route-providers-read-only" to "false"
        )
        return ConfigFactory.parseMap(extraConfig).withFallback(config)
    }
}