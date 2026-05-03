package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.common.message.requireActor
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import com.mikai233.protocol.ProtoRpc.PlayerLoginReq
import com.mikai233.protocol.ProtoRpc.PlayerLoginResp
import com.mikai233.protocol.testNotify
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler
import kotlin.random.Random

@AllOpen
@Suppress("unused")
class PlayerLoginReqHandler : MessageHandler<PlayerLoginReq> {
    override fun handle(context: HandlerContext, message: PlayerLoginReq) {
        val actor = context.requireActor<PlayerActor>()
        actor.bindChannelActorPath(message.channelActorPath)
        val response = loginService.loginSuccessResp(actor)
        actor.sender.tell(PlayerLoginResp.newBuilder().setResponse(response).build())
        actor.self tell PlayerLoginEvent
        repeat(100) {
            actor.send(
                testNotify {
                    data = Random.nextDouble().toString()
                },
            )
        }
    }
}
