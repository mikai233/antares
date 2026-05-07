package com.mikai233.player.service.chat

import com.mikai233.common.broadcast.Topic
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.protocol.ProtoChat
import com.mikai233.protocol.ProtoChat.ChatSendResult

class DefaultChatPolicy(
    private val config: ChatPolicyConfig = ChatPolicyConfig(),
    private val guards: List<ChatSendGuard> = defaultGuards(config),
    private val deliveryResolver: ChatDeliveryResolver = DefaultChatDeliveryResolver,
    private val rateLimiter: ChatRateLimiter = ChatRateLimiter(
        config.rateLimitWindowMillis,
        config.maxMessagesPerWindow,
    ),
    private val nowMillis: () -> Long = ::unixTimestamp,
) : ChatPolicy {
    override val maxOfflinePrivateMessagesPerLogin: Int
        get() = config.maxOfflinePrivateMessagesPerLogin

    override fun decideSend(sender: ChatParticipant, request: ProtoChat.ChatSendReq): ChatSendDecision {
        val context = ChatSendContext(
            sender = sender,
            request = request,
            content = request.content.trim(),
            nowMillis = nowMillis(),
        )
        guards.firstNotNullOfOrNull { guard -> guard.reject(context) }?.let {
            return it
        }
        return when (val delivery = deliveryResolver.resolve(context)) {
            is ChatDeliveryResolution.Accepted -> {
                rejectRateLimited(sender, context.nowMillis)
                    ?: ChatSendDecision.Accept(context.content, delivery.delivery)
            }

            is ChatDeliveryResolution.Rejected -> {
                ChatSendDecision.Reject(delivery.result, delivery.reason)
            }
        }
    }

    override fun clearRateLimit(playerId: Long) {
        rateLimiter.clear(playerId)
    }

    private fun rejectRateLimited(sender: ChatParticipant, nowMillis: Long): ChatSendDecision.Reject? {
        val retryAfterMillis = rateLimiter.hit(sender.playerId, nowMillis)
        if (retryAfterMillis <= 0) {
            return null
        }
        return ChatSendDecision.Reject(
            ChatSendResult.ChatSendRateLimited,
            "rate limited",
            retryAfterMillis = retryAfterMillis,
        )
    }
}

private fun defaultGuards(config: ChatPolicyConfig): List<ChatSendGuard> {
    return listOf(
        PlayerIdentityGuard,
        ContentGuard(config.maxContentLength),
        MutedGuard,
        BlockedKeywordGuard(config.blockedKeywords),
    )
}

private object PlayerIdentityGuard : ChatSendGuard {
    override fun reject(context: ChatSendContext): ChatSendDecision.Reject? {
        if (context.request.playerId == context.sender.playerId) {
            return null
        }
        return ChatSendDecision.Reject(ChatSendResult.ChatSendRejected, "player_id mismatch")
    }
}

private class ContentGuard(
    private val maxContentLength: Int,
) : ChatSendGuard {
    override fun reject(context: ChatSendContext): ChatSendDecision.Reject? {
        return when {
            context.content.isBlank() -> {
                ChatSendDecision.Reject(ChatSendResult.ChatSendEmptyContent, "empty content")
            }

            context.content.length > maxContentLength -> {
                ChatSendDecision.Reject(ChatSendResult.ChatSendContentTooLong, "content too long")
            }

            else -> null
        }
    }
}

private object MutedGuard : ChatSendGuard {
    override fun reject(context: ChatSendContext): ChatSendDecision.Reject? {
        if (context.sender.mutedUntil <= context.nowMillis) {
            return null
        }
        return ChatSendDecision.Reject(
            ChatSendResult.ChatSendMuted,
            "player is muted",
            mutedUntil = context.sender.mutedUntil,
        )
    }
}

private class BlockedKeywordGuard(
    private val blockedKeywords: Set<String>,
) : ChatSendGuard {
    override fun reject(context: ChatSendContext): ChatSendDecision.Reject? {
        val normalized = context.content.lowercase()
        if (blockedKeywords.none(normalized::contains)) {
            return null
        }
        return ChatSendDecision.Reject(ChatSendResult.ChatSendSensitiveContent, "sensitive content")
    }
}

private object DefaultChatDeliveryResolver : ChatDeliveryResolver {
    override fun resolve(context: ChatSendContext): ChatDeliveryResolution {
        return when (context.request.channel) {
            ProtoChat.ChatChannel.Private -> resolvePrivateDelivery(context)
            ProtoChat.ChatChannel.World -> ChatDeliveryResolution.Accepted(
                ChatDelivery.Broadcast(
                    context.sender.worldId,
                    Topic.ofWorld(context.sender.worldId),
                ),
            )

            ProtoChat.ChatChannel.Alliance -> resolveAllianceDelivery(context)
            ProtoChat.ChatChannel.CrossWorld -> ChatDeliveryResolution.Accepted(
                ChatDelivery.Broadcast(
                    context.sender.worldId,
                    Topic.CROSS_WORLD_CHAT,
                ),
            )

            ProtoChat.ChatChannel.System,
            ProtoChat.ChatChannel.ChatChannelUnspecified,
            ProtoChat.ChatChannel.UNRECOGNIZED,
            null,
                -> ChatDeliveryResolution.Rejected(ChatSendResult.ChatSendInvalidChannel, "invalid channel")
        }
    }

    private fun resolvePrivateDelivery(context: ChatSendContext): ChatDeliveryResolution {
        if (context.request.targetId <= 0 || context.request.targetId == context.sender.playerId) {
            return ChatDeliveryResolution.Rejected(
                ChatSendResult.ChatSendInvalidTarget,
                "invalid private target",
            )
        }
        return ChatDeliveryResolution.Accepted(ChatDelivery.Private(context.request.targetId))
    }

    private fun resolveAllianceDelivery(context: ChatSendContext): ChatDeliveryResolution {
        if (context.request.targetId <= 0 || context.request.targetId != context.sender.allianceId) {
            return ChatDeliveryResolution.Rejected(ChatSendResult.ChatSendInvalidTarget, "invalid alliance")
        }
        return ChatDeliveryResolution.Accepted(
            ChatDelivery.Broadcast(context.sender.worldId, Topic.ofAlliance(context.request.targetId)),
        )
    }
}
