package com.mikai233.common.excel

import com.alibaba.excel.EasyExcel

class EasyExcelContext(val path: String, headerNum: Int = 4) : ExcelContext {
    override val header: List<List<String>>
    override val body: List<List<String>>

    init {
        val rows = EasyExcel.read(path).headRowNumber(0).doReadAllSync<LinkedHashMap<Int, String>>()
        val header = rows.slice(0 until headerNum)
        this.header = header.map { it.values.toList() }
        val body = rows.slice(4 until rows.size)
        this.body = body.map { it.values.toList() }
    }

    override fun forEachRow(row: (ExcelReader) -> Unit) {
        body.forEach { rowData ->
            row(EasyExcelReader(header[0].mapIndexed { index, s -> s to index }.associate { it }, rowData))
        }
    }
}

class EasyExcelReader(private val columnName: Map<String, Int>, private val row: List<String>) : ExcelReader {
    override fun data(): List<String> {
        return row
    }

    override fun read(name: String): String {
        val index = requireNotNull(columnName[name]) { "index of name:$name not found" }
        return row[index]
    }
}