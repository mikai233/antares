package com.mikai233.common.excel

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class RefFor(val config: KClass<out ExcelConfig<*, *>>)
