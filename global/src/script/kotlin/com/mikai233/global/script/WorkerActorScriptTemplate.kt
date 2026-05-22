package com.mikai233.global.script

import com.mikai233.global.actor.WorkerActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext

class WorkerActorScriptTemplate : ActorScript<WorkerActor>() {
    override fun executeActor(context: ActorScriptContext<WorkerActor>) {
        context.actor.logger.info("{}", this::class.qualifiedName)
    }
}
