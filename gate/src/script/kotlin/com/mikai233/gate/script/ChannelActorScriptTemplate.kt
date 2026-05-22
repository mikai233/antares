package com.mikai233.gate.script

import com.mikai233.gate.ChannelActor
import io.github.realmlabs.asteria.script.ActorScript
import io.github.realmlabs.asteria.script.ActorScriptContext

class ChannelActorScriptTemplate : ActorScript<ChannelActor>() {
    override fun executeActor(context: ActorScriptContext<ChannelActor>) {
        context.actor.logger.info("ChannelActorScriptTemplate")
    }
}
