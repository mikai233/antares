package com.mikai233.player.script

import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.player.PlayerActor
import kotlin.Unit

class TestGroovyActorScript implements ActorScriptFunction<PlayerActor> {
    @Override
    Unit invoke(PlayerActor playerActor) {
        playerActor.logger.info("hello groovy")
        return null
    }
}
