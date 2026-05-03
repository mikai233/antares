package com.mikai233.player.handler.gm

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.logger
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.gmResp

@AllOpen
@Suppress("unused")
class TestGmHandler {
    private val logger = logger()

    fun handle(actor: PlayerActor, params: List<String>) {
        logger.info("testGm with params: {}", params)
        actor.send(gmResp { success = true })
    }
}
