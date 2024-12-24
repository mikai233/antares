package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoTest.TestReq

@AllOpen
class TestHandler : MessageHandler {
    fun handleTestReq(player: PlayerActor, testReq: TestReq) {

    }
}
