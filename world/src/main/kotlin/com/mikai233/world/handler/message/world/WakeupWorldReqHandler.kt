package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.requireActor
import com.mikai233.protocol.ProtoRpc.WorldWakeupReq
import com.mikai233.protocol.ProtoRpc.WorldWakeupResp
import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class WakeupWorldReqHandler : MessageHandler<HandlerContext, WorldWakeupReq> {
    override fun handle(context: HandlerContext, message: WorldWakeupReq) {
        val actor = context.requireActor<WorldActor>()
        actor.sender tell WorldWakeupResp.newBuilder().build()
    }
}
