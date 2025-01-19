package com.mikai233.processor

import com.google.devtools.ksp.symbol.KSType

internal val KryoSupportTypes = setOf(
    "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
    "kotlin.Boolean", "kotlin.Char", "kotlin.String", "kotlin.Byte",
    "kotlin.Short", "kotlin.Unit",
)


// 判断是否为Kryo支持的类型
internal fun isKryoSupportType(kType: KSType): Boolean {
    return KryoSupportTypes.contains(kType.declaration.qualifiedName?.asString())
}
