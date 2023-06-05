package com.mikai233.common.excel

import kotlin.reflect.KClass

annotation class Validate(val v: KClass<out Validator<in ExcelRow<*>, *>>)
