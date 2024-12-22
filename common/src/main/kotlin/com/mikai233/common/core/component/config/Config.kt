package com.mikai233.common.core.component.config

import com.mikai233.common.core.Node
import kotlin.reflect.KClass

interface Config

data class ZookeeperConfigMeta(
    val clazz: KClass<out Config>?,
    val name: String,
    val data: String?,
    val children: List<ZookeeperConfigMeta>,
)

class ZookeeperConfigCache(val node: Node, val clazz: KClass<out Config>) {
    fun onUpdate() {

    }
}