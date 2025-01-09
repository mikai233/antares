package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.logger
import com.mikai233.common.message.Gm
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.gmResp

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
@AllOpen
@Suppress("unused")
class PlayerHandler : MessageHandler {
    val logger = logger()

    @Handle
    fun handleGmReq(player: PlayerActor, req: GmReq) {
        invokeOnTargetMode(ServerMode.DevMode) { player.node.gmDispatcher.dispatch(req.cmd, player, req.paramsList) }
    }

    @Gm("testGm")
    fun handleTestGm(player: PlayerActor, params: List<String>) {
        logger.info("testGm with params: {}", params)
        player.send(gmResp { success = true })
    }
}