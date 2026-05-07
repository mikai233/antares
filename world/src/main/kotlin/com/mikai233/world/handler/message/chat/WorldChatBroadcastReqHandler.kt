package com.mikai233.world.handler.message.chat

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.protocol.ProtoRpcChat.WorldChatBroadcastReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class WorldChatBroadcastReqHandler : WorldMessageHandler<WorldChatBroadcastReq> {
    override fun handle(context: WorldHandlerContext, message: WorldChatBroadcastReq) {
        context.actor.broadcast(
            message.message,
            message.topic,
            message.includeList.toSet(),
            message.excludeList.toSet(),
        )
    }
}
