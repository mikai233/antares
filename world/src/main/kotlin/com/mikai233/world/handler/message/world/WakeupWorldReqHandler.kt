package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.extension.tell
import com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq
import com.mikai233.protocol.ProtoRpcWorld.WorldWakeupResp
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
class WakeupWorldReqHandler : WorldMessageHandler<WorldWakeupReq> {
    override fun handle(context: WorldHandlerContext, message: WorldWakeupReq) {
        val actor = context.actor
        actor.sender tell WorldWakeupResp.newBuilder().build()
    }
}
