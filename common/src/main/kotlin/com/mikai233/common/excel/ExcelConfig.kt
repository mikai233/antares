package com.mikai233.common.excel

import com.google.common.collect.ImmutableMap

abstract class ExcelConfig<K, R>(protected val manager: ExcelManager) where R : ExcelRow<K> {
    abstract fun rows(): ImmutableMap<K, R>

    abstract fun name(): String

    operator fun get(pk: K) = requireNotNull(rows()[pk]) { "pk:$pk row data not found in rows" }

    fun contains(pk: K) = rows().containsKey(pk)

    inline fun forEach(action: (Map.Entry<K, R>) -> Unit) {
        rows().forEach(action)
    }

    abstract fun load(context: ExcelContext)

    open fun rebuildData() {}

    open fun allLoadFinish() {}
}