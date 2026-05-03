package com.mikai233.gate.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.pingResp

@AllOpen
@Suppress("unused")
class PingHandler {
    fun handlePingReq(actor: ChannelActor) {
        actor.write(pingResp { serverTimestamp = unixTimestamp() })
    }
}
