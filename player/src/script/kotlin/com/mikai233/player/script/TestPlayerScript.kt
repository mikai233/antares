package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.player.PlayerActor

class TestPlayerScript : ActorScriptFunction<PlayerActor> {
    private val logger = logger()

    override fun invoke(player: PlayerActor, p2: ByteArray?) {
        logger.info("playerId:{} hello world", player.playerId)
    }
}
