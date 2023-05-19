package com.mikai233.player.script

import com.mikai233.common.core.components.Role
import com.mikai233.common.ext.logger
import com.mikai233.player.service.LoginService
import com.mikai233.player.service.loginService
import com.mikai233.shared.script.NodeRoleScriptFunction

class LoginServiceFix : NodeRoleScriptFunction, LoginService() {
    override val role: Role = Role.Player
    private val logger = logger()

    override fun invoke() {
        loginService = this
        logger.info("fix login service done")
    }
}