package com.mikai233.shared.excel

import com.google.common.collect.ImmutableMap
import com.mikai233.common.excel.*
import com.mikai233.common.ext.camelCaseToSnakeCase
import kotlinx.serialization.Serializable

@Serializable
data class MoneyRow(
    val id: Int,
    val name: String,
    val initialValue: Int,
    val limit: Long,
) : ExcelRow<Int> {
    override fun `primary key`(): Int = id
}

@Serializable
class MoneyConfig : ExcelConfig<Int, MoneyRow>() {
    override fun name(): String = "money.xlsx"

    override fun load(context: ExcelContext, manager: ExcelManager) {
        val rowsBuilder = ImmutableMap.builder<Int, MoneyRow>()
        context.forEachRow { row ->
            val id = row.readInt(MoneyRow::id.name.camelCaseToSnakeCase())
            val name = row.read(MoneyRow::name.name.camelCaseToSnakeCase())
            val initialValue = row.readInt(MoneyRow::initialValue.name.camelCaseToSnakeCase())
            val limit = row.readLong(MoneyRow::limit.name.camelCaseToSnakeCase())
            val rowData = MoneyRow(
                id = id,
                name = name,
                initialValue = initialValue,
                limit = limit
            )
            rowsBuilder.put(rowData.`primary key`(), rowData)
        }
        rows = rowsBuilder.build()
    }
}
