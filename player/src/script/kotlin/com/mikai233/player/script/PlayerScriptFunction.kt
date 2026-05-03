package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.core.patchableServices
import com.mikai233.player.PlayerNode
import com.mikai233.player.service.LoginService
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class PlayerScriptFunction : NodeScript() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext): ScriptExecutionResult? {
        val node = context.runtime as PlayerNode
        node.patchableServices.register(LoginService::class, LoginServiceFix())
        logger.info("fix login service done")
        return null
    }
}
