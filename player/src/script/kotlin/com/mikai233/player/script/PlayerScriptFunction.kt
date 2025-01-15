package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.script.NodeRoleScriptFunction
import com.mikai233.player.PlayerNode
import com.mikai233.player.service.loginService

class PlayerScriptFunction : NodeRoleScriptFunction<PlayerNode> {
    private val logger = logger()

    override fun invoke(p1: PlayerNode, p2: ByteArray?) {
        loginService = LoginServiceFix()
        logger.info("fix login service done")
    }
}