package com.mikai233.common.excel

import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@Serializable
abstract class ExcelConfig<K, R> : SerdeConfig where R : ExcelRow<K> {
    open lateinit var rows: Map<K, R>

    abstract fun name(): String

    operator fun get(pk: K) = requireNotNull(rows[pk]) { "pk:$pk row data not found in rows" }

    fun contains(pk: K) = rows.containsKey(pk)

    inline fun forEach(action: (Map.Entry<K, R>) -> Unit) {
        rows.forEach(action)
    }

    abstract fun load(context: ExcelContext, manager: ExcelManager)

    open fun rebuildData(manager: ExcelManager) {}

    open fun allLoadFinish(manager: ExcelManager) {}

    fun report(manager: ExcelManager, message: String) {
        manager.report("[${javaClass.simpleName}] ${name()}: $message")
    }

    fun verify(manager: ExcelManager, value: Boolean, message: String) {
        if (!value) {
            report(manager, message)
        }
    }

    fun <T : Any> verifyNotNull(manager: ExcelManager, value: T?, message: String, onNotNull: (value: T) -> Unit) {
        if (value == null) {
            report(manager, message)
        } else {
            onNotNull(value)
        }
    }
}
