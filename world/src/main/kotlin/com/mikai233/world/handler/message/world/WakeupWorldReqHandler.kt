package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.protocol.ProtoRpc.WorldWakeupReq
import com.mikai233.protocol.ProtoRpc.WorldWakeupResp
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler

@AllOpen
class WakeupWorldReqHandler : WorldMessageHandler<WorldWakeupReq> {
    override fun handle(context: WorldHandlerContext, message: WorldWakeupReq) {
        val actor = context.actor
        actor.sender tell WorldWakeupResp.newBuilder().build()
    }
}
