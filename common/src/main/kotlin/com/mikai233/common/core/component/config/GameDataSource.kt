package com.mikai233.common.core.component.config

data class GameDataSource(val sources: List<Source>) : Config {
    override fun path(): String {
        return GAME_DATA_SOURCE
    }
}

data class Source(val host: String, val port: Int)