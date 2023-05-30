package com.mikai233.common.excel

@Suppress("FunctionName")
interface ExcelRow<K> : SerdeRow {
    fun `primary key`(): K

    fun `primary key that cannot be modified`(): String = `primary key`().toString()
}