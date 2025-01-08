package com.mikai233.gate.script

import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.gate.ChannelActor

class TestChannelActorScript : ActorScriptFunction<ChannelActor> {
    override fun invoke(p1: ChannelActor, p2: ByteArray?) {
        p1.logger.info("TestChannelActorScript")
    }
}