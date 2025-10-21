package com.mikai233.tools.excel

import com.alibaba.excel.context.AnalysisContext
import com.alibaba.excel.event.AnalysisEventListener
import com.mikai233.common.extension.snakeCaseToCamelCase

class ExcelHeaderListener(val completeCallback: (ExcelField?, List<ExcelField>) -> Unit) :
    AnalysisEventListener<Map<Int, String?>>() {
    //表头名称到列号的映射
    private val nameIndex: MutableMap<String, Int> = mutableMapOf()

    //列号到数据类型的映射
    private val typeIndex: MutableMap<Int, String> = mutableMapOf()

    //作用域
    private val scopeIndex = mutableMapOf<Int, ScopeType>()

    //注释
    private val commentIndex: MutableMap<Int, String> = mutableMapOf()

    override fun invoke(data: Map<Int, String?>, context: AnalysisContext) = Unit

    override fun invokeHeadMap(
        headMap: Map<Int, String?>,
        context: AnalysisContext,
    ) {
        when (context.readRowHolder().rowIndex) {
            0 -> {
                headMap.forEach { (k, v) ->
                    nameIndex[requireNotNull(v) { "null value at row 1 column ${k + 1}" }] = k
                }
            }

            1 -> {
                headMap.forEach { (k, v) ->
                    typeIndex[k] = requireNotNull(v) { "null value at row 2 column ${k + 1}" }
                }
            }

            2 -> {
                headMap.forEach { (k, v) ->
                    scopeIndex[k] = ScopeType.fromString(requireNotNull(v) { "null value at row 3 column ${k + 1}" })
                }
            }

            4 -> {
                headMap.forEach { (k, v) ->
                    commentIndex[k] = v?.replace("\n", " ") ?: ""
                }
            }
        }
    }

    override fun doAfterAllAnalysed(context: AnalysisContext) {
        var idField: ExcelField? = null
        val fields = nameIndex.entries.sortedBy { it.value }.mapNotNull { (name, index) ->
            val scope = scopeIndex[index] ?: ScopeType.Other
            if (scope == ScopeType.Other) {
                null
            } else {
                val typeStr = typeIndex[index]
                val type = TYPE_MAPPING[typeStr] ?: error("未知的数据类型：$typeStr")
                val field = ExcelField(
                    name.snakeCaseToCamelCase(),
                    name,
                    type,
                    commentIndex[index] ?: "",
                )
                if (scope == ScopeType.AllKey && idField == null) {
                    idField = field
                }
                field
            }
        }
        completeCallback(idField ?: fields.firstOrNull(), fields)
    }
}
