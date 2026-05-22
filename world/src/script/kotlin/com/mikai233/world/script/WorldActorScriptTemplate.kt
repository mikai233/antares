package com.mikai233.world.script

import com.mikai233.common.extension.logger
import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext

class WorldActorScriptTemplate : ActorScript<WorldActor>() {
    private val logger = logger()

    override fun executeActor(context: ActorScriptContext<WorldActor>) {
        val world = context.actor
        logger.info("test script for world: {}", world.worldId)
    }
}
