package com.mikai233.world.handler.message.chat

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.protocol.ProtoChat.ChatChannel
import com.mikai233.protocol.ProtoChat.ChatMessageNotify
import com.mikai233.protocol.ProtoRpcChat.RpcChatMessage
import com.mikai233.protocol.ProtoRpcChat.WorldChatBroadcastReq
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class WorldChatBroadcastReqHandler : WorldMessageHandler<WorldChatBroadcastReq> {
    override fun handle(context: WorldHandlerContext, message: WorldChatBroadcastReq) {
        context.actor.broadcast(
            message.message.toNotify(),
            message.topic,
            message.includeList.toSet(),
            message.excludeList.toSet(),
        )
    }

    private fun RpcChatMessage.toNotify(): ChatMessageNotify {
        return ChatMessageNotify.newBuilder()
            .setMessageId(messageId)
            .setChannel(ChatChannel.forNumber(channel) ?: ChatChannel.UNRECOGNIZED)
            .setFromPlayerId(fromPlayerId)
            .setFromName(fromName)
            .setTargetId(targetId)
            .setContent(content)
            .setSentAt(sentAt)
            .setWorldId(worldId)
            .build()
    }
}
