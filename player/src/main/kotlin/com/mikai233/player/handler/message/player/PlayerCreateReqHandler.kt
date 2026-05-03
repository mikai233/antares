package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.requireActor
import com.mikai233.common.message.player.PlayerCreateReq
import com.mikai233.common.message.player.PlayerCreateResp
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class PlayerCreateReqHandler : MessageHandler<PlayerCreateReq> {
    override fun handle(context: HandlerContext, message: PlayerCreateReq) {
        val actor = context.requireActor<PlayerActor>()
        actor.bindChannelActor(message.channelActor)
        loginService.createPlayer(actor, message)
        actor.sender.tell(PlayerCreateResp)
        val response = loginService.loginSuccessResp(actor)
        actor.send(response)
    }
}
