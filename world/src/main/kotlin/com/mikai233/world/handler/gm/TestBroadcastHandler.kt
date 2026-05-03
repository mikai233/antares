package com.mikai233.world.handler.gm

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.broadcast.Topic
import com.mikai233.protocol.testNotify
import com.mikai233.world.PlayerSession
import com.mikai233.world.WorldActor

@AllOpen
class TestBroadcastHandler {
    fun handle(actor: WorldActor, session: PlayerSession, params: List<String>) {
        actor.broadcast(testNotify { }, Topic.ofWorld(actor.worldId), emptySet(), emptySet())
    }
}
