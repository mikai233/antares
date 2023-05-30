package com.mikai233.shared.excel

import com.google.common.collect.ImmutableMap
import com.mikai233.common.annotation.NoArg
import com.mikai233.common.excel.*
import com.mikai233.common.ext.camelCaseToSnakeCase
import kotlinx.serialization.Serializable

@NoArg
@Serializable
data class ItemRow(
    val id: Int,
    val quality: Int,
    val stackingUpperLimit: Int,
) : ExcelRow<Int> {
    override fun `primary key`(): Int = id
}

@Serializable
class ItemConfig : ExcelConfig<Int, ItemRow>() {
    override fun name(): String = "item.xlsx"

    override fun load(context: ExcelContext, manager: ExcelManager) {
        val rowsBuilder = ImmutableMap.builder<Int, ItemRow>()
        context.forEachRow { row ->
            val id = row.readInt(ItemRow::id.name.camelCaseToSnakeCase())
            val quality = row.readInt(ItemRow::quality.name.camelCaseToSnakeCase())
            val stackingUpperLimit = row.readInt(ItemRow::stackingUpperLimit.name.camelCaseToSnakeCase())
            val rowData = ItemRow(
                id = id,
                quality = quality,
                stackingUpperLimit = stackingUpperLimit
            )
            rowsBuilder.put(rowData.`primary key`(), rowData)
        }
        rows = rowsBuilder.build()
    }
}
