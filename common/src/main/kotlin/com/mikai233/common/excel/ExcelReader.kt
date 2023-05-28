package com.mikai233.common.excel

import com.google.common.collect.ImmutableList

typealias IntTriple = Triple<Int, Int, Int>
typealias IntPair = Pair<Int, Int>

typealias LongTriple = Triple<Long, Long, Long>
typealias LongPair = Pair<Long, Long>

typealias IntTripleList = ImmutableList<IntTriple>
typealias IntPairList = ImmutableList<IntPair>

typealias LongTripList = ImmutableList<LongTriple>
typealias LongPairList = ImmutableList<LongPair>

interface ExcelReader {
    fun data(): List<String>
    fun read(name: String): String
}

fun ExcelReader.readInt(name: String) = read(name).toInt()

fun ExcelReader.readLong(name: String) = read(name).toLong()

fun ExcelReader.readDouble(name: String) = read(name).toDouble()

fun ExcelReader.readBoolean(name: String): Boolean {
    return when (read(name)) {
        "0", "false" -> false
        "1", "true" -> true
        else -> error("illegal boolean value:${name}, allowed:[0,1,false,true]")
    }
}

fun ExcelReader.readIntPair(name: String): IntPair = read(name).split(",").toIntPair()

fun ExcelReader.readIntTriple(name: String): IntTriple = read(name).split(",").toIntTriple()

fun ExcelReader.readVecInt(name: String): ImmutableList<Int> =
    ImmutableList.copyOf(read(name).split(",").map { it.toInt() })

fun ExcelReader.readVec2Int(name: String): IntPairList =
    ImmutableList.copyOf(read(name).split(";").map {
        it.split(",").toIntPair()
    })

fun ExcelReader.readVec3Int(name: String): IntTripleList =
    ImmutableList.copyOf(read(name).split(";").map {
        it.split(",").toIntTriple()
    })

fun List<String>.toIntPair(): IntPair {
    check(size == 2) { "illegal pair data:$this , expect like 1,2" }
    return get(0).toInt() to get(1).toInt()
}

fun List<String>.toIntTriple(): IntTriple {
    check(size == 3) { "illegal trip data:$this , expect like 1,2,3;1,2,3" }
    return Triple(get(0).toInt(), get(1).toInt(), get(2).toInt())
}
