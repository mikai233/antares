package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoRpcPlayer.PlayerChannelClosedReq

@AllOpen
class PlayerChannelClosedReqHandler : PlayerMessageHandler<PlayerChannelClosedReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerChannelClosedReq) {
        context.actor.clearChannelActor()
    }
}
