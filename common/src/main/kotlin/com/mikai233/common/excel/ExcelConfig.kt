package com.mikai233.common.excel

abstract class ExcelConfig<K, R>(val manager: ExcelManager) where R : ExcelRow<K> {
    abstract fun rows(): Map<K, R>

    abstract fun name(): String

    abstract fun load(context: ExcelContext)

    fun rebuildData() {}

    fun allLoadFinish() {}
}