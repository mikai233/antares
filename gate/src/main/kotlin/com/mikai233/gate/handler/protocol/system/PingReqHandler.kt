package com.mikai233.gate.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.gate.ChannelActor
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.pingResp
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PingReqHandler : MessageHandler<ActorHandlerContext<ChannelActor>, PingReq> {
    override fun handle(context: ActorHandlerContext<ChannelActor>, message: PingReq) {
        val actor = context.actor
        actor.write(pingResp { serverTimestamp = unixTimestamp() })
    }
}
