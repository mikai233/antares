package com.mikai233.common.excel

import com.mikai233.common.ext.snakeCaseToCamelCase

enum class Type {
    Int,
    Long,
    Double,
    Boolean,
    String,
    Lang,
    IntPair,
    IntTriple,
    VecInt,
    Vec2Int,
    Vec3Int,
    ;

    companion object {
        fun form(str: kotlin.String): Type {
            return requireNotNull(
                Type.values().find {
                    it.name.equals(str, true) || str.snakeCaseToCamelCase().equals(it.name, true)
                }) { "cell type:$str not define in code" }
        }
    }
}
