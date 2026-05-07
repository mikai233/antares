package com.mikai233.player.handler.protocol.chat

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoChat.ChatHistoryReq
import io.github.realmlabs.asteria.message.AsteriaGatewayRoute
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
@AsteriaGatewayRoute(
    route = "player",
    entityId = "session:player_id",
    inject = ["player_id=route.entity_id"],
)
class ChatHistoryReqHandler : PlayerMessageHandler<ChatHistoryReq> {
    override fun handle(context: PlayerHandlerContext, message: ChatHistoryReq) {
        val actor = context.actor
        actor.node.chatService.handleHistory(actor, message)
    }
}
