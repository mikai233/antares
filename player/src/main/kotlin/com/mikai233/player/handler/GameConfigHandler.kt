package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.Handle
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.playerService

@AllOpen
@Suppress("unused")
class GameConfigHandler : MessageHandler {
    @Handle(GameConfigUpdateEvent::class)
    fun handleGameConfigUpdate(player: PlayerActor) {
        playerService.onGameConfigUpdated(player)
    }
}
