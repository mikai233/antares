package com.mikai233.world.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import com.mikai233.world.handler.gm.TestBroadcastHandler
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class GmReqHandler(
    private val testBroadcastHandler: TestBroadcastHandler,
) : WorldMessageHandler<GmReq> {
    override fun handle(context: WorldHandlerContext, message: GmReq) {
        val actor = context.actor
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
