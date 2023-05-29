package com.mikai233.shared.excel

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.mikai233.common.annotation.NoArg
import com.mikai233.common.excel.*
import com.mikai233.common.ext.camelCaseToSnakeCase

@NoArg
data class EventSupplyRow(
    val id: Int,
    val startTime: String,
    val endTime: String,
    val position: Int,
    val cost: Triple<Int, Int, Int>,
    val reward: ImmutableList<Triple<Int, Int, Int>>,
) : ExcelRow<Int> {
    override fun `primary key`(): Int = id
}

class EventSupplyConfig : ExcelConfig<Int, EventSupplyRow>() {
    override fun name(): String = "event_supply.xlsx"

    override fun load(context: ExcelContext, manager: ExcelManager) {
        val rowsBuilder = ImmutableMap.builder<Int, EventSupplyRow>()
        context.forEachRow { row ->
            val id = row.readInt(EventSupplyRow::id.name.camelCaseToSnakeCase())
            val startTime = row.read(EventSupplyRow::startTime.name.camelCaseToSnakeCase())
            val endTime = row.read(EventSupplyRow::endTime.name.camelCaseToSnakeCase())
            val position = row.readInt(EventSupplyRow::position.name.camelCaseToSnakeCase())
            val cost = row.readIntTriple(EventSupplyRow::cost.name.camelCaseToSnakeCase())
            val reward = row.readVec3Int(EventSupplyRow::reward.name.camelCaseToSnakeCase())
            val rowData = EventSupplyRow(
                id = id,
                startTime = startTime,
                endTime = endTime,
                position = position,
                cost = cost,
                reward = reward
            )
            rowsBuilder.put(rowData.`primary key`(), rowData)
        }
        rows = rowsBuilder.build()
    }
}
