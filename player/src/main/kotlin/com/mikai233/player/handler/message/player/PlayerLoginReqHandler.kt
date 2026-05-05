package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.extension.tell
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.runtime.system
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginReq
import com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginResp
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class PlayerLoginReqHandler : PlayerMessageHandler<PlayerLoginReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerLoginReq) {
        val actor = context.actor
        actor.bindChannelActor(message.channelActor.decodeActorRef(actor.node.system))
        val response = actor.node.loginService.loginSuccessResp(actor)
        actor.sender.tell(PlayerLoginResp.newBuilder().setResponse(response).build())
        actor.self tell PlayerLoginEvent
    }
}
