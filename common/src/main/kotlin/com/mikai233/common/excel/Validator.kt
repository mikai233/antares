package com.mikai233.common.excel

interface Validator<C, R, K> where C : ExcelConfig<K, R>, R : ExcelRow<K> {
    fun name(): String

    fun validate(config: C, row: R, manager: ExcelManager): Boolean
}
