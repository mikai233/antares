package com.mikai233.shared.excel

import com.mikai233.common.excel.ExcelConfig
import com.mikai233.common.excel.ExcelManager

class TestConfig(manager: ExcelManager) : ExcelConfig<Int>(manager) {
    private val rows: MutableMap<Int, TestRow> = mutableMapOf()

    init {
        println("init")
    }

    override fun rows(): Map<Int, TestRow> {
        return rows
    }

    override fun name(): String {
        return "test.xlsx"
    }

    override fun load() {
        TODO("Not yet implemented")
    }
}

fun main() {
    val manager = ExcelManager()
    manager.loadExcel("com.mikai233.shared.excel")
}