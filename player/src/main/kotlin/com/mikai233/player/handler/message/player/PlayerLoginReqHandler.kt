package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.system
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.service.loginService
import com.mikai233.protocol.ProtoRpc.PlayerLoginReq
import com.mikai233.protocol.ProtoRpc.PlayerLoginResp
import com.mikai233.protocol.testNotify
import kotlin.random.Random

@AllOpen
class PlayerLoginReqHandler : PlayerMessageHandler<PlayerLoginReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerLoginReq) {
        val actor = context.actor
        actor.bindChannelActor(message.channelActor.decodeActorRef(actor.node.system))
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
