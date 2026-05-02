package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.player.PlayerActor
import io.github.mikai233.asteria.script.ActorScript
import io.github.mikai233.asteria.script.ActorScriptContext
import io.github.mikai233.asteria.script.ScriptExecutionResult

class TestPlayerScript : ActorScript<PlayerActor>() {
    private val logger = logger()

    override fun executeActor(context: ActorScriptContext<PlayerActor>): ScriptExecutionResult? {
        val player = context.actor
        logger.info("playerId:{} hello world", player.playerId)
        player.node.gameWorldConfigCache.forEach { (id, config) ->
            logger.info("id:{} config:{}", id, config)
        }
        return null
    }
}
