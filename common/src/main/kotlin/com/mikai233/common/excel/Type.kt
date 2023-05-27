package com.mikai233.common.excel

enum class Type {
    Int,
    Long,
    Double,
    Boolean,
    String,
    VecInt,
    Vec2Int,
    Vec3Int,
    ;

    companion object {
        fun form(str: kotlin.String): Type {
            return requireNotNull(
                Type.values().find { it.name.equals(str, true) }) { "cell type:$str not define in code" }
        }
    }
}