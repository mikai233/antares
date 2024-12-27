package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.script.NodeRoleScriptFunction
import com.mikai233.player.PlayerNode
import com.mikai233.player.service.LoginService
import com.mikai233.player.service.loginService

class HomeScriptFunction : NodeRoleScriptFunction<PlayerNode>, LoginService() {
    class LoginServiceFix : LoginService() {

    }

    private val logger = logger()

    override fun invoke(playerNode: PlayerNode) {
        loginService = LoginServiceFix()
        logger.info("fix login service done")
    }
}