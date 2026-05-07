package com.mikai233.player.handler.message.chat

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoRpcChat.PlayerAllianceChangedReq
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class PlayerAllianceChangedReqHandler : PlayerMessageHandler<PlayerAllianceChangedReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerAllianceChangedReq) {
        val actor = context.actor
        actor.node.chatService.handleAllianceChanged(actor, message)
    }
}
