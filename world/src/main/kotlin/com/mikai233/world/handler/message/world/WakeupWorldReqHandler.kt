package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.requireActor
import com.mikai233.common.message.world.WakeupWorldReq
import com.mikai233.common.message.world.WakeupWorldResp
import com.mikai233.world.WorldActor
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class WakeupWorldReqHandler : MessageHandler<WakeupWorldReq> {
    override fun handle(context: HandlerContext, message: WakeupWorldReq) {
        val actor = context.requireActor<WorldActor>()
        actor.sender tell WakeupWorldResp
    }
}
