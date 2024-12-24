package com.mikai233.player.handler

import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.shared.constants.PlayerActionType
import com.mikai233.shared.message.ExcelUpdate

class ExcelHandler : MessageHandler {
    fun handleExcelUpdate(player: PlayerActor, excelUpdate: ExcelUpdate) {
        val action = player.manager.get<PlayerActionMem>().getOrCreateAction(PlayerActionType.ExcelVersion)
        if (excelUpdate.hashcode.toLong() != action.actionParam) {
            //event
            action.actionParam = excelUpdate.hashcode.toLong()
        }
    }
}