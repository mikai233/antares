package com.mikai233.player.script

import com.mikai233.player.PlayerNode
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import org.jetbrains.annotations.NotNull
import org.slf4j.LoggerFactory

class TestGroovyScript extends NodeScript<PlayerNode> {
    private def logger = LoggerFactory.getLogger(TestGroovyScript.class)

    @Override
    void executeNode(@NotNull NodeScriptContext<PlayerNode> context) {
        logger.info("hello world")
    }
}
