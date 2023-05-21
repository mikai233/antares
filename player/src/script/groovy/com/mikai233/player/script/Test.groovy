package com.mikai233.player.script

import com.mikai233.common.core.components.Role
import com.mikai233.player.PlayerNode
import com.mikai233.shared.script.NodeRoleScriptFunction
import kotlin.Unit
import org.slf4j.LoggerFactory

class TestGroovyScript implements NodeRoleScriptFunction<PlayerNode> {
    private def logger = LoggerFactory.getLogger(TestGroovyScript.class)

    @Override
    Unit invoke(PlayerNode playerNode) {
        logger.info("hello world")
        return null
    }

    @Override
    Role getRole() {
        return Role.Player
    }
}