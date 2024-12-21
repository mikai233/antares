package com.mikai233.tools.excel

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.typeNameOf
import java.util.*


internal const val INTERFACE_PACKAGE = "com.mikai233.shared.excel"

internal const val GENERATE_PACKAGE = "com.mikai233.shared.config"

internal val GAME_CONFIG = ClassName(INTERFACE_PACKAGE, "GameConfig")

internal val GAME_CONFIGS = ClassName(INTERFACE_PACKAGE, "GameConfigs")

internal val TYPE_MAPPING = mapOf(
    "uint" to Int::class.asTypeName(),
    "int" to Int::class.asTypeName(),
    "long" to Long::class.asTypeName(),
    "string" to String::class.asTypeName(),
    "bool" to Boolean::class.asTypeName(),
    "vector3_array_int" to typeNameOf<List<Triple<Int, Int, Int>>>(),
    "vector3_int" to typeNameOf<Triple<Int, Int, Int>>(),
    "vector2_int" to typeNameOf<Pair<Int, Int>>(),
    "vector3_uint" to typeNameOf<Triple<Int, Int, Int>>(),
    "vector2_uint" to typeNameOf<Pair<Int, Int>>(),
    "vector2_array_int" to typeNameOf<List<Pair<Int, Int>>>(),
    "array_int" to typeNameOf<List<Int>>(),
    "array_uint" to typeNameOf<List<Int>>(),
    "dictionary_string_float" to typeNameOf<Map<String, Float>>(),
    "dictionary_string_int" to typeNameOf<Map<String, Int>>(),
    "lang" to String::class.asTypeName(),
    "float" to Float::class.asTypeName(),
    "double" to Double::class.asTypeName(),
)

internal val PARSE_MAPPING = mapOf(
    String::class.asTypeName() to "parseString",
    Int::class.asTypeName() to "parseInt",
    Long::class.asTypeName() to "parseLong",
    Boolean::class.asTypeName() to "parseBoolean",
    Float::class.asTypeName() to "parseFloat",
    Double::class.asTypeName() to "parseDouble",
    typeNameOf<List<Int>>() to "parseIntArray",
    typeNameOf<Pair<Int, Int>>() to "parseIntPair",
    typeNameOf<Triple<Int, Int, Int>>() to "parseIntTriple",
    typeNameOf<List<Pair<Int, Int>>>() to "parseIntPairArray",
    typeNameOf<List<Triple<Int, Int, Int>>>() to "parseIntTripleArray",
    typeNameOf<List<Long>>() to "parseLongArray",
    typeNameOf<List<Pair<Long, Long>>>() to "parseLongPairArray",
    typeNameOf<List<Triple<Long, Long, Long>>>() to "parseLongTripleArray",
    typeNameOf<Map<String, Float>>() to "parseStringFloatMap",
    typeNameOf<Map<String, Int>>() to "parseStringIntMap",
)

enum class ScopeType {
    AllKey,
    All,
    Server,
    Other,
    ;

    companion object {
        fun fromString(value: String): ScopeType {
            return when (value.lowercase(Locale.getDefault())) {
                "allkey" -> AllKey
                "all" -> All
                "server" -> Server
                else -> Other
            }
        }
    }
}