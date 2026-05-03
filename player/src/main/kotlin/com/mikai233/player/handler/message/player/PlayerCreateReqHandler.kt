package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.ActorHandlerContext
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import com.mikai233.protocol.ProtoRpc.PlayerCreateReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateResp
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerCreateReqHandler : MessageHandler<ActorHandlerContext<PlayerActor>, PlayerCreateReq> {
    override fun handle(context: ActorHandlerContext<PlayerActor>, message: PlayerCreateReq) {
        val actor = context.actor
        actor.bindChannelActorPath(message.channelActorPath)
        loginService.createPlayer(actor, message)
        val response = loginService.loginSuccessResp(actor)
        actor.sender.tell(PlayerCreateResp.newBuilder().setResponse(response).build())
    }
}
