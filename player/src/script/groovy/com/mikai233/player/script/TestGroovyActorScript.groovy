package com.mikai233.player.script

import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext
import org.jetbrains.annotations.NotNull

class TestGroovyActorScript extends ActorScript<PlayerActor> {
    @Override
    void executeActor(@NotNull ActorScriptContext<PlayerActor> context) {
        context.actor.logger.info("hello groovy")
    }
}
