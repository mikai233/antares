package com.mikai233.common.excel

interface ExcelContext {
    val header: List<List<String>>
    val body: List<List<String>>

    fun forEachRow(row: (ExcelReader) -> Unit)
}