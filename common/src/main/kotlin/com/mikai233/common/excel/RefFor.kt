package com.mikai233.common.excel

import kotlin.reflect.KClass

annotation class RefFor<K>(val row: KClass<out ExcelRow<K>>)
