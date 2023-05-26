package com.mikai233.common.excel

interface ExcelRow<K> {
    fun id(): K

    fun unModifyKey(): String = id().toString()
}