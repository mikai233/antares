package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.common.runtime.system
import com.mikai233.protocol.ProtoRpcPlayer.PlayerChannelClosedReq
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class PlayerChannelClosedReqHandler : PlayerMessageHandler<PlayerChannelClosedReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerChannelClosedReq) {
        val actor = context.actor
        actor.clearChannelActor()
        if (message.shutdown) {
            actor.shutdownForPlan(
                planId = message.shutdownPlanId,
                coordinator = message.coordinatorActor.decodeActorRef(actor.node.system),
            )
        }
    }
}
