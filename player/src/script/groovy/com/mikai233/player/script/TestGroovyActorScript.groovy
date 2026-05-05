package com.mikai233.player.script

import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class TestGroovyActorScript extends ActorScript<PlayerActor> {
    @Override
    ScriptExecutionResult executeActor(ActorScriptContext<PlayerActor> context) {
        context.actor.logger.info("hello groovy")
        return null
    }
}
