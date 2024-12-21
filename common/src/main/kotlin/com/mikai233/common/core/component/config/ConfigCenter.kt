package com.mikai233.common.core.component.config

import com.mikai233.common.extension.Json

interface ConfigCenter {
    fun addConfig(config: Config)
    fun getConfig(path: String): ByteArray
    fun deleteConfig(path: String)
    fun watchConfig(path: String, onUpdate: (ByteArray) -> Unit)
    fun getChildren(path: String): List<String>
    fun exists(path: String): Boolean
}

inline fun <reified C : Config> ConfigCenter.getConfigEx(path: String): C {
    val bytes: ByteArray = getConfig(path)
    return Json.fromJson<C>(bytes)
}
