package com.mikai233.player.handler.protocol.test

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoTest.TestReq
import com.mikai233.protocol.testResp
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
class TestReqHandler : MessageHandler<TestReq> {
    override fun handle(context: HandlerContext, message: TestReq) {
        val actor = context.requireActor<PlayerActor>()
        actor.send(testResp { })
    }
}
