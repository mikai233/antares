package com.mikai233.shared.excel

enum class DataScope {
    Key,
    Server,
    All,
    Other,
    ;

    companion object {
        fun form(str: String): DataScope {
            return when (str.lowercase()) {
                "key", "allkey" -> Key
                "server", "sever" -> Server
                "all" -> All
                else -> Other
            }
        }
    }
}
