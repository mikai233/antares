package com.mikai233.common.excel

import com.mikai233.common.ext.snakeCaseToCamelCase

enum class ExcelKey {
    PrimaryKey,
    Client,
    Server,
    All,
    ;

    companion object {
        fun form(str: String): ExcelKey {
            return requireNotNull(
                ExcelKey.values().find {
                    it.name.equals(str, true) || str.snakeCaseToCamelCase().equals(it.name, true)
                }) { "cell type:$str not define in code" }
        }
    }
}
