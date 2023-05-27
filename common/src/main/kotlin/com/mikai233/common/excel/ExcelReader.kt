package com.mikai233.common.excel

import com.google.common.collect.ImmutableList

typealias IntTrip = Triple<Int, Int, Int>
typealias IntPair = Pair<Int, Int>

typealias LongTrip = Triple<Long, Long, Long>
typealias LongPair = Pair<Long, Long>

typealias IntTripList = ImmutableList<IntTrip>
typealias IntPairList = ImmutableList<IntPair>

typealias LongTripList = ImmutableList<LongTrip>
typealias LongPairList = ImmutableList<LongPair>

interface ExcelReader {
    fun data(): Map<Int, String>
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

fun ExcelReader.readVecInt(name: String): ImmutableList<Int> =
    ImmutableList.copyOf(read(name).split(",").map { it.toInt() })

fun ExcelReader.readVec2Int(name: String): IntPairList =
    ImmutableList.copyOf(read(name).split(";").map {
        it.split(",").let { list ->
            check(list.size == 2) { "illegal vec2int data:$list, expect like 1,2;2,3" }
            list[0].toInt() to list[1].toInt()
        }
    })

fun ExcelReader.readVec3Int(name: String): IntTripList =
    ImmutableList.copyOf(read(name).split(";").map {
        it.split(",").let { list ->
            check(list.size == 3) { "illegal vec2int data:$list, expect like 1,2;2,3" }
            Triple(list[0].toInt(), list[1].toInt(), list[2].toInt())
        }
    })
