package com.mikai233.shared.excel

import com.mikai233.common.excel.ExcelManager


fun main() {
    val manager = ExcelManager()
    manager.loadExcel("F:\\MiscProjects\\workspace\\Design\\ExportSheet", "com.mikai233.shared.excel")
    val config = manager.getConfig<QuestionTotalConfig>()
    config.forEach {
        println(it)
    }
}