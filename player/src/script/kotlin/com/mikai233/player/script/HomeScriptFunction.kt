package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.player.HomeNode
import com.mikai233.player.service.LoginService
import com.mikai233.player.service.loginService
import com.mikai233.common.script.NodeRoleScriptFunction

class HomeScriptFunction : NodeRoleScriptFunction<HomeNode>, LoginService() {
    class LoginServiceFix : LoginService() {

    }

    private val logger = logger()

    override fun invoke(homeNode: HomeNode) {
        loginService = LoginServiceFix()
        logger.info("fix login service done")
    }
}