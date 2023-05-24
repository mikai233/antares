package com.mikai233.common.excel

interface Validator<R, K> where R : ExcelRow<K> {
    fun validate(row: R, manager: ExcelManager): Boolean
}