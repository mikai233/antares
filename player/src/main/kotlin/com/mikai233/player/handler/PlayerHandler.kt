package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatch
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.playerService
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.gmResp

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
@AllOpen
@Suppress("unused")
class PlayerHandler {
    val logger = logger()

    fun handleGmReq(player: PlayerActor, req: GmReq) {
        invokeOnTargetMode(ServerMode.DevMode) { player.node.gmDispatcher.dispatch(player, req.cmd, req.paramsList) }
    }

    fun handlePlayerLoginEvent(player: PlayerActor) {
        tryCatch(logger) { playerService.onGameConfigUpdated(player) }
    }

    fun handlePlayerCreateEvent(player: PlayerActor) {
        player.self tell PlayerLoginEvent
    }

    fun handleGameConfigUpdatedEvent(player: PlayerActor) = Unit

    fun handleTestGm(player: PlayerActor, params: List<String>) {
        logger.info("testGm with params: {}", params)
        player.send(gmResp { success = true })
    }
}
