package com.mikai233.global.script

import com.mikai233.global.actor.WorkerActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class WorkerActorScriptTemplate : ActorScript<WorkerActor>() {
    override fun executeActor(context: ActorScriptContext<WorkerActor>): ScriptExecutionResult? {
        context.actor.logger.info("{}", this::class.qualifiedName)
        return null
    }
}
