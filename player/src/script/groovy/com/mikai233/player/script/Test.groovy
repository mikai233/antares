package com.mikai233.player.script

import com.mikai233.common.core.component.Role
import com.mikai233.player.HomeNode
import com.mikai233.common.script.NodeRoleScriptFunction
import kotlin.Unit
import org.slf4j.LoggerFactory

class TestGroovyScript implements NodeRoleScriptFunction<HomeNode> {
    private def logger = LoggerFactory.getLogger(TestGroovyScript.class)

    @Override
    Unit invoke(HomeNode playerNode) {
        logger.info("hello world")
        return null
    }

    @Override
    Role getRole() {
        return Role.Player
    }
}