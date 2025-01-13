package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoTest.TestReq
import com.mikai233.protocol.testResp

@AllOpen
@Suppress("unused")
class TestHandler : MessageHandler {
    @Handle
    fun handleTestReq(player: PlayerActor, testReq: TestReq) {
        player.send(testResp { })
    }
}
