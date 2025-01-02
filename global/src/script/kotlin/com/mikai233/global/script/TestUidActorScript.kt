package com.mikai233.global.script

import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.global.actor.UidActor

class TestUidActorScript : ActorScriptFunction<UidActor> {
    override fun invoke(p1: UidActor) {
        p1.logger.info("com.mikai233.global.script.TestUidActorScript")
    }
}