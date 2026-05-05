package com.mikai233.player.script

import com.mikai233.common.config.luban.tbItem
import com.mikai233.common.core.gameConfigSnapshot
import com.mikai233.common.core.gameWorldConfigs
import com.mikai233.common.extension.logger
import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class PlayerActorScriptTemplate : ActorScript<PlayerActor>() {
    private val logger = logger()

    override fun executeActor(context: ActorScriptContext<PlayerActor>): ScriptExecutionResult? {
        val player = context.actor
        logger.info("playerId:{} hello world", player.playerId)
        player.node.gameWorldConfigs.forEach { (id, config) ->
            logger.info("id:{} config:{}", id, config)
        }
        val item = player.node.gameConfigSnapshot.tbItem.get(1001)
        logger.info("demo item config:{}", item)
        return null
    }
}
