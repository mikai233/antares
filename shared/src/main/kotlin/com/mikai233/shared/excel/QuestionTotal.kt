package com.mikai233.shared.excel

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.mikai233.common.excel.*
import com.mikai233.common.ext.camelCaseToSnakeCase

data class QuestionTotal(
    val id: Int,
    val cost: Int,
    val rowReward: ImmutableList<Int>,
    val columnReward: ImmutableList<Int>,
    val totalReward: Int,
) : ExcelRow<Int> {
    override fun `primary key`(): Int = id
}

class QuestionTotalConfig(
    manager: ExcelManager,
) : ExcelConfig<Int, QuestionTotal>(manager) {
    private lateinit var rows: ImmutableMap<Int, QuestionTotal>

    override fun rows(): ImmutableMap<Int, QuestionTotal> = rows

    override fun name(): String = "question_total_reward.xlsx"

    override fun load(context: ExcelContext) {
        val rowsBuilder = ImmutableMap.builder<Int, QuestionTotal>()
        context.forEachRow { row ->
            val id = row.readInt(QuestionTotal::id.name.camelCaseToSnakeCase())
            val cost = row.readInt(QuestionTotal::cost.name.camelCaseToSnakeCase())
            val rowReward = row.readVecInt(QuestionTotal::rowReward.name.camelCaseToSnakeCase())
            val columnReward = row.readVecInt(QuestionTotal::columnReward.name.camelCaseToSnakeCase())
            val totalReward = row.readInt(QuestionTotal::totalReward.name.camelCaseToSnakeCase())
            val rowData = QuestionTotal(
                id = id, cost = cost, rowReward = rowReward, columnReward =
                columnReward, totalReward = totalReward
            )
            rowsBuilder.put(rowData.`primary key`(), rowData)
        }
        rows = rowsBuilder.build()
    }
}

