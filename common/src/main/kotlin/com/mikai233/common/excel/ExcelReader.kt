package com.mikai233.common.excel

import com.mikai233.common.ext.Json

interface ExcelReader {
    fun read(name: String): String
}

fun ExcelReader.readInt(name: String) = read(name).toInt()

fun ExcelReader.readUInt(name: String) = read(name).toUInt()

fun ExcelReader.readLong(name: String) = read(name).toLong()

fun ExcelReader.readULong(name: String) = read(name).toULong()

inline fun <reified T> ExcelReader.readJson(name: String): T {
    return Json.fromJson(read(name))
}

fun ExcelReader.readBoolean(name: String): Boolean {
    return when (read(name)) {
        "0", "false" -> false
        "1", "true" -> true
        else -> error("illegal boolean value:${name}, allowed:[0,1,false,true]")
    }
}