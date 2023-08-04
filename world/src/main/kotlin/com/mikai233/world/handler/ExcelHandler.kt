package com.mikai233.world.handler

import com.mikai233.common.msg.MessageHandler
import com.mikai233.shared.constants.WorldActionType
import com.mikai233.shared.message.ExcelUpdate
import com.mikai233.world.WorldActor
import com.mikai233.world.data.WorldActionMem

class ExcelHandler : MessageHandler {
    fun handleExcelUpdate(world: WorldActor, excelUpdate: ExcelUpdate) {
        val action = world.manager.get<WorldActionMem>().getOrCreateAction(WorldActionType.ExcelVersion)
        if (excelUpdate.hashcode.toLong() != action.actionParam) {
            //event
            action.actionParam = excelUpdate.hashcode.toLong()
        }
    }
}