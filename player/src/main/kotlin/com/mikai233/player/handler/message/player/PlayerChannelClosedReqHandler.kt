package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.requireActor
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoRpc.PlayerChannelClosedReq
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerChannelClosedReqHandler : MessageHandler<PlayerChannelClosedReq> {
    override fun handle(context: HandlerContext, message: PlayerChannelClosedReq) {
        context.requireActor<PlayerActor>().clearChannelActorPath()
    }
}
