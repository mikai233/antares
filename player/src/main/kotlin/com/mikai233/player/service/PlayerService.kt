package com.mikai233.player.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.constants.PlayerActionType
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerActionMem

@AllOpen
class PlayerService {
    fun onGameConfigUpdated(player: PlayerActor) {
        val action = player.manager.get<PlayerActionMem>().getOrCreateAction(PlayerActionType.GameConfigVersion)
        val longHashcode = player.node.gameConfigManagerHashcode.asLong()
        if (longHashcode != action.actionParam) {
            action.actionParam = longHashcode
            player.fireEvent(GameConfigUpdatedEvent)
        }
    }
}
