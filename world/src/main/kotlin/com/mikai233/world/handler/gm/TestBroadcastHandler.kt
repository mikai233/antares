package com.mikai233.world.handler.gm

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.broadcast.Topic
import com.mikai233.protocol.ProtoTest.TestResp
import com.mikai233.world.PlayerSession
import com.mikai233.world.WorldActor

@AllOpen
class TestBroadcastHandler {
    fun handle(
        actor: WorldActor,
        @Suppress("UNUSED_PARAMETER") session: PlayerSession,
        @Suppress("UNUSED_PARAMETER") params: List<String>,
    ) {
        actor.broadcast(TestResp.getDefaultInstance(), Topic.ofWorld(actor.worldId), emptySet(), emptySet())
    }
}
