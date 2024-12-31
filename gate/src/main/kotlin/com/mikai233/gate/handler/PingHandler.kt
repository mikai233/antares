package com.mikai233.gate.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.pingResp

@AllOpen
class PingHandler : MessageHandler {
    @Handle(PingReq::class)
    fun handlePingReq(actor: ChannelActor) {
        actor.write(pingResp { serverTimestamp = unixTimestamp() })
    }
}