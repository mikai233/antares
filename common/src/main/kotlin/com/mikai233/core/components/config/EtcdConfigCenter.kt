package com.mikai233.core.components.config

import com.mikai233.core.components.Component

class EtcdConfigCenter : Component, ConfigCenter {
    override fun getConfig(path: String): Config {
        TODO("Not yet implemented")
    }

    override fun deleteConfig(path: String) {
        TODO("Not yet implemented")
    }

    override fun watchConfig(path: String, onUpdate: (Config) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun getChildren(): List<String> {
        TODO("Not yet implemented")
    }

    override fun init() {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }
}

fun main() {
}