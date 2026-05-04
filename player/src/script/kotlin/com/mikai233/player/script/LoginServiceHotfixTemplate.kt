package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.core.replacePatchableService
import com.mikai233.player.PlayerNode
import com.mikai233.player.service.LoginService
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class LoginServiceHotfixTemplate : NodeScript<PlayerNode>() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext<PlayerNode>): ScriptExecutionResult? {
        context.runtime.replacePatchableService(
            LoginService::class,
            LoginServiceHotfix(),
            patchId = "script:login-service-hotfix",
        )
        logger.info("login service hotfix installed on player node")
        return null
    }
}
