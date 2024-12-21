package com.mikai233.tools.excel

import com.squareup.kotlinpoet.TypeName

data class ExcelField(
    val name: String,
    val originName: String,
    val type: TypeName,
    val comment: String,
)
