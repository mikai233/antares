package com.mikai233.player.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.message.requireActor
import com.mikai233.player.handler.gm.TestGmHandler
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoSystem.GmReq
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class GmReqHandler(
    private val testGmHandler: TestGmHandler,
) : MessageHandler<GmReq> {
    override fun handle(context: HandlerContext, message: GmReq) {
        val actor = context.requireActor<PlayerActor>()
        invokeOnTargetMode(ServerMode.DevMode) {
            when (message.cmd) {
                "testGm" -> testGmHandler.handle(actor, message.paramsList)
                else -> error("gm handler for command=${message.cmd} not found")
            }
        }
    }
}
