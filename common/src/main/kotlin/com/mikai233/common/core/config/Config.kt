package com.mikai233.common.core.config

import kotlin.reflect.KClass

/**
 * @param path 在zookeeper中的全路径
 * @param data 配置数据
 */
data class Config(
    val path: String,
    val data: Any?,
)

data class ConfigMeta(
    val clazz: KClass<*>,
    val name: String,
    val data: String?,
    val children: Map<String, ConfigMeta>,
)