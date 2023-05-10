package com.mikai233.core.components.config

interface ConfigCenter {
    fun getConfig(path: String): Config
    fun deleteConfig(path: String)
    fun watchConfig(path: String, onUpdate: (Config) -> Unit)
    fun getChildren(): List<String>
}