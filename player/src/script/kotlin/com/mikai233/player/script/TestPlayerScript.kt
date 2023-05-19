package com.mikai233.player.script

import com.mikai233.common.ext.logger
import com.mikai233.player.PlayerActor
import com.mikai233.shared.script.ActorScriptFunction

class TestPlayerScript : ActorScriptFunction<PlayerActor> {
    private val logger = logger()
    override fun invoke(player: PlayerActor) {
        logger.info("playerId:{} hello world", player.playerId)
    }
}