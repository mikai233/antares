package com.mikai233.player.handler.protocol.test

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoTest.TestReq
import com.mikai233.protocol.testResp
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class TestReqHandler : MessageHandler<ActorHandlerContext<PlayerActor>, TestReq> {
    override fun handle(context: ActorHandlerContext<PlayerActor>, message: TestReq) {
        val actor = context.actor
        actor.send(testResp { })
    }
}
