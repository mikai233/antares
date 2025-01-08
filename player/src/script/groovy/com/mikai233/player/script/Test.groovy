package com.mikai233.player.script

import com.mikai233.common.script.NodeRoleScriptFunction
import com.mikai233.player.PlayerNode
import kotlin.Unit
import org.slf4j.LoggerFactory

class TestGroovyScript implements NodeRoleScriptFunction<PlayerNode> {
    private def logger = LoggerFactory.getLogger(TestGroovyScript.class)

    @Override
    Unit invoke(PlayerNode playerNode, byte[] bytes) {
        logger.info("hello world")
        return null
    }
}