package com.mikai233.core.components.config

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/11
 */
data class AkkaConfig(val host: String) : Config {
    override fun path(): String {
        return "/game/$host"
    }
}
