package com.mikai233.common.core.component.config

data class NettyConfig(val host: String, val port: Int) : Config {
    override fun path(): String {
        return serverNetty(host)
    }
}
