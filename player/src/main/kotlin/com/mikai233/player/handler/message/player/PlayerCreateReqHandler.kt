package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.system
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.extension.tell
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.service.loginService
import com.mikai233.protocol.ProtoRpc.PlayerCreateReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateResp

@AllOpen
class PlayerCreateReqHandler : PlayerMessageHandler<PlayerCreateReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerCreateReq) {
        val actor = context.actor
        actor.bindChannelActor(message.channelActor.decodeActorRef(actor.node.system))
        loginService.createPlayer(actor, message)
        val response = loginService.loginSuccessResp(actor)
        actor.sender.tell(PlayerCreateResp.newBuilder().setResponse(response).build())
    }
}
