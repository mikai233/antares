package com.mikai233.common.excel

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.common.collect.ImmutableMap

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class ExcelConfig<K, R> where R : ExcelRow<K> {
    open lateinit var rows: ImmutableMap<K, R>

    abstract fun name(): String

    operator fun get(pk: K) = requireNotNull(rows[pk]) { "pk:$pk row data not found in rows" }

    fun contains(pk: K) = rows.containsKey(pk)

    inline fun forEach(action: (Map.Entry<K, R>) -> Unit) {
        rows.forEach(action)
    }

    abstract fun load(context: ExcelContext, manager: ExcelManager)

    open fun rebuildData(manager: ExcelManager) {}

    open fun allLoadFinish(manager: ExcelManager) {}
}