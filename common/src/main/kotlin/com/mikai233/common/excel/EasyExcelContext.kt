package com.mikai233.common.excel

import com.alibaba.excel.EasyExcel
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap

class EasyExcelContext(val path: String) : ExcelContext {
    override fun forEachRow(row: (ExcelReader) -> Unit) {
        val rows = EasyExcel.read(path).headRowNumber(0).doReadAllSync<Map<Int, String>>()
        val header = rows.slice(0 until 4)
        val body = rows.slice(4 until rows.size)
        body.forEach { rowData ->
            row(EasyExcelReader(HashBiMap.create(header[0]).inverse(), rowData))
        }
    }
}

class EasyExcelReader(private val header: BiMap<String, Int>, private val row: Map<Int, String>) : ExcelReader {
    override fun data(): Map<Int, String> {
        return row
    }

    override fun read(name: String): String {
        val index = requireNotNull(header[name]) { "index of name:$name not found" }
        return requireNotNull(row[index]) { "row of index:${index} not found" }
    }
}