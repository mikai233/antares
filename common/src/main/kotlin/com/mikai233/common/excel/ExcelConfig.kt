package com.mikai233.common.excel

abstract class ExcelConfig<K>(val manager: ExcelManager) {
    abstract fun rows(): Map<K, ExcelRow<K>>

    abstract fun name(): String

    abstract fun load()

    fun rebuildData() {}

    fun allLoadFinish() {}
}