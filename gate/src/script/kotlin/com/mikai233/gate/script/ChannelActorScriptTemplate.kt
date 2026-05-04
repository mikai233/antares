package com.mikai233.gate.script

import com.mikai233.gate.ChannelActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class ChannelActorScriptTemplate : ActorScript<ChannelActor>() {
    override fun executeActor(context: ActorScriptContext<ChannelActor>): ScriptExecutionResult? {
        context.actor.logger.info("ChannelActorScriptTemplate")
        return null
    }
}
