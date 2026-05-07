package com.mikai233.player.handler.protocol.chat

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoChat.ChatSendReq
import io.github.realmlabs.asteria.message.AsteriaGatewayRoute
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
@AsteriaGatewayRoute(
    route = "player",
    entityId = "session:player_id",
    inject = ["player_id=route.entity_id"],
)
class ChatSendReqHandler : PlayerMessageHandler<ChatSendReq> {
    override fun handle(context: PlayerHandlerContext, message: ChatSendReq) {
        val actor = context.actor
        actor.node.chatService.handleSend(actor, message)
    }
}
