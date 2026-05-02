package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.player.PlayerNode
import com.mikai233.player.service.loginService
import io.github.mikai233.asteria.script.NodeScript
import io.github.mikai233.asteria.script.NodeScriptContext
import io.github.mikai233.asteria.script.ScriptExecutionResult

class PlayerScriptFunction : NodeScript() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext): ScriptExecutionResult? {
        context.runtime as PlayerNode
        loginService = LoginServiceFix()
        logger.info("fix login service done")
        return null
    }
}
