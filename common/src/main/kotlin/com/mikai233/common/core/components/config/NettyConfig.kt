package com.mikai233.common.core.components.config

data class NettyConfig(val host: String, val port: Int) : Config {
    override fun path(): String {
        return serverNetty(host)
    }
}
