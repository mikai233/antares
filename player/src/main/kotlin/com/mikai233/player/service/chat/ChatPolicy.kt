package com.mikai233.player.service.chat

import com.mikai233.protocol.ProtoChat.ChatSendReq
import com.mikai233.protocol.ProtoChat.ChatSendResult

interface ChatPolicy {
    val maxOfflinePrivateMessagesPerLogin: Int

    fun decideSend(sender: ChatParticipant, request: ChatSendReq, nowMillis: Long): ChatSendDecision

    fun clearRateLimit(playerId: Long)
}

data class ChatPolicyConfig(
    val maxContentLength: Int = 200,
    val maxOfflinePrivateMessagesPerLogin: Int = 100,
    val rateLimitWindowMillis: Long = 10_000L,
    val maxMessagesPerWindow: Int = 5,
    val blockedKeywords: Set<String> = setOf("badword"),
)

data class ChatParticipant(
    val playerId: Long,
    val worldId: Long,
    val allianceId: Long,
    val mutedUntil: Long,
)

data class ChatSendContext(
    val sender: ChatParticipant,
    val request: ChatSendReq,
    val content: String,
    val nowMillis: Long,
)

sealed interface ChatSendDecision {
    data class Accept(
        val content: String,
        val delivery: ChatDelivery,
    ) : ChatSendDecision

    data class Reject(
        val result: ChatSendResult,
        val reason: String,
        val mutedUntil: Long = 0,
        val retryAfterMillis: Long = 0,
    ) : ChatSendDecision
}

sealed interface ChatDelivery {
    data class Private(val targetPlayerId: Long) : ChatDelivery

    data class Broadcast(
        val worldId: Long,
        val topic: String,
    ) : ChatDelivery
}

sealed interface ChatDeliveryResolution {
    data class Accepted(val delivery: ChatDelivery) : ChatDeliveryResolution

    data class Rejected(
        val result: ChatSendResult,
        val reason: String,
    ) : ChatDeliveryResolution
}

fun interface ChatSendGuard {
    fun reject(context: ChatSendContext): ChatSendDecision.Reject?
}

fun interface ChatDeliveryResolver {
    fun resolve(context: ChatSendContext): ChatDeliveryResolution
}
