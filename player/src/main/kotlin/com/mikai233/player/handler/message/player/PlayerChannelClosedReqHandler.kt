package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoRpc.PlayerChannelClosedReq
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerChannelClosedReqHandler : MessageHandler<ActorHandlerContext<PlayerActor>, PlayerChannelClosedReq> {
    override fun handle(context: ActorHandlerContext<PlayerActor>, message: PlayerChannelClosedReq) {
        context.actor.clearChannelActorPath()
    }
}
