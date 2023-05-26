package com.mikai233.common.excel

interface ExcelContext {
    fun forEachRow(row: (ExcelReader) -> Unit)
}