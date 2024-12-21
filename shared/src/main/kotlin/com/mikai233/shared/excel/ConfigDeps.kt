package com.mikai233.shared.excel

import kotlin.reflect.KClass

/**
 * 配置类依赖的类，这个会注册到Kryo中，用于序列化和反序列化
 * 使用静态注册而不使用运行时反射的原因是为了加快启动速度
 */
val CONFIG_DEPS = arrayOf<KClass<*>>(
    GameConfigManager::class,
)