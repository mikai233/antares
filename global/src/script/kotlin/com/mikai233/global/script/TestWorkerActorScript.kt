package com.mikai233.global.script

import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.global.actor.WorkerActor

class TestWorkerActorScript : ActorScriptFunction<WorkerActor> {
    override fun invoke(p1: WorkerActor) {
        p1.logger.info("com.mikai233.global.script.TestUidActorScript")
    }
}