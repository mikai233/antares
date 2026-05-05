package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.runtime.system
import com.mikai233.protocol.ProtoRpcWorld.WorldShutdownReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class WorldShutdownReqHandler : WorldMessageHandler<WorldShutdownReq> {
    override fun handle(context: WorldHandlerContext, message: WorldShutdownReq) {
        val actor = context.actor
        actor.shutdownForPlan(
            planId = message.shutdownPlanId,
            coordinator = message.coordinatorActor.decodeActorRef(actor.node.system),
        )
    }
}
