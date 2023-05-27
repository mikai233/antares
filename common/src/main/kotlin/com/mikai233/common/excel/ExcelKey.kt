package com.mikai233.common.excel

enum class ExcelKey {
    PrimaryKey,
    Client,
    Server,
    All,
    ;

    companion object {
        fun form(str: String): ExcelKey {
            return requireNotNull(
                ExcelKey.values().find { it.name.equals(str, true) }) { "cell type:$str not define in code" }
        }
    }
}