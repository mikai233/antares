package com.mikai233.gate.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.requireActor
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.pingResp
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PingReqHandler : MessageHandler<PingReq> {
    override fun handle(context: HandlerContext, message: PingReq) {
        val actor = context.requireActor<ChannelActor>()
        actor.write(pingResp { serverTimestamp = unixTimestamp() })
    }
}
