package com.mikai233.player.script

import com.mikai233.common.core.component.Role
import com.mikai233.common.extension.logger
import com.mikai233.player.PlayerNode
import com.mikai233.player.service.LoginService
import com.mikai233.player.service.loginService
import com.mikai233.shared.script.NodeRoleScriptFunction

class LoginServiceFix : NodeRoleScriptFunction<PlayerNode>, LoginService() {
    override val role: Role = Role.Player
    private val logger = logger()

    override fun invoke(playerNode: PlayerNode) {
        loginService = this
        logger.info("fix login service done")
    }
}