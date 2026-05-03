package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.protocol.ProtoRpc.WorldWakeupReq
import com.mikai233.protocol.ProtoRpc.WorldWakeupResp
import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class WakeupWorldReqHandler : MessageHandler<ActorHandlerContext<WorldActor>, WorldWakeupReq> {
    override fun handle(context: ActorHandlerContext<WorldActor>, message: WorldWakeupReq) {
        val actor = context.actor
        actor.sender tell WorldWakeupResp.newBuilder().build()
    }
}
