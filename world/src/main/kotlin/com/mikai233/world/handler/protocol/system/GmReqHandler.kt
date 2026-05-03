package com.mikai233.world.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.message.requireActor
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.world.WorldActor
import com.mikai233.world.handler.gm.TestBroadcastHandler
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class GmReqHandler(
    private val testBroadcastHandler: TestBroadcastHandler,
) : MessageHandler<GmReq> {
    override fun handle(context: HandlerContext, message: GmReq) {
        val actor = context.requireActor<WorldActor>()
        val session = actor.sessionManager[message.playerId]
            ?: error("session not found for playerId=${message.playerId}")
        invokeOnTargetMode(ServerMode.DevMode) {
            when (message.cmd) {
                "testBroadcast" -> testBroadcastHandler.handle(actor, session, message.paramsList)
                else -> error("gm handler for command=${message.cmd} not found")
            }
        }
    }
}
