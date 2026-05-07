package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.extension.tell
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.runtime.system
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoRpcPlayer.PlayerCreateReq
import com.mikai233.protocol.ProtoRpcPlayer.PlayerCreateResp
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class PlayerCreateReqHandler : PlayerMessageHandler<PlayerCreateReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerCreateReq) {
        val actor = context.actor
        actor.bindChannelActor(message.channelActor.decodeActorRef(actor.node.system))
        actor.node.loginService.createPlayer(actor, message)
        val response = actor.node.loginService.loginSuccessResp(actor)
        actor.sender.tell(PlayerCreateResp.newBuilder().setResponse(response).build())
        actor.node.chatService.subscribeCurrentAllianceTopic(actor)
        actor.node.chatService.deliverOfflinePrivateMessages(actor)
        actor.self tell PlayerCreateEvent
    }
}
