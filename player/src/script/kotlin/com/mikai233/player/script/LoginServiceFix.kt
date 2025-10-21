package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.message.player.PlayerCreateReq
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.LoginService

class LoginServiceFix : LoginService() {
    val logger = logger()
    override fun createPlayer(player: PlayerActor, playerCreateReq: PlayerCreateReq) {
        logger.info("fix logic")
        super.createPlayer(player, playerCreateReq)
    }
}