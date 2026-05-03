package com.mikai233.player.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.handler.gm.TestGmHandler
import com.mikai233.protocol.ProtoSystem.GmReq

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
class GmReqHandler(
    private val testGmHandler: TestGmHandler,
) : PlayerMessageHandler<GmReq> {
    override fun handle(context: PlayerHandlerContext, message: GmReq) {
        val actor = context.actor
        invokeOnTargetMode(ServerMode.DevMode) {
            when (message.cmd) {
                "testGm" -> testGmHandler.handle(actor, message.paramsList)
                else -> error("gm handler for command=${message.cmd} not found")
            }
        }
    }
}
