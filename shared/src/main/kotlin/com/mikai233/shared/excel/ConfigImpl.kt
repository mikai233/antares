package com.mikai233.shared.excel

import kotlin.reflect.KClass

/**
 * 所有[GameConfig]的实现类
 * 使用静态注册而不使用运行时反射的原因是为了加快启动速度
 */
val CONFIG_IMPL = arrayOf<KClass<out V>>(

)