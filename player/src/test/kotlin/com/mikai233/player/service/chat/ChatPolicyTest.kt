package com.mikai233.player.service.chat

import com.mikai233.common.broadcast.Topic
import com.mikai233.protocol.ProtoChat.ChatChannel
import com.mikai233.protocol.ProtoChat.ChatSendReq
import com.mikai233.protocol.ProtoChat.ChatSendResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatPolicyTest {
    @Test
    fun `accepts private chat and trims content`() {
        val decision = policy().decideSend(participant(), request(ChatChannel.Private, 2, " hello "))

        val accepted = decision as ChatSendDecision.Accept
        val delivery = accepted.delivery as ChatDelivery.Private
        assertEquals("hello", accepted.content)
        assertEquals(2, delivery.targetPlayerId)
    }

    @Test
    fun `rejects invalid private target`() {
        val decision = policy().decideSend(participant(), request(ChatChannel.Private, PLAYER_ID))

        val rejected = decision as ChatSendDecision.Reject
        assertEquals(ChatSendResult.ChatSendInvalidTarget, rejected.result)
        assertEquals("invalid private target", rejected.reason)
    }

    @Test
    fun `accepts alliance chat only for current alliance`() {
        val decision = policy().decideSend(
            participant(allianceId = ALLIANCE_ID),
            request(ChatChannel.Alliance, ALLIANCE_ID),
        )

        val accepted = decision as ChatSendDecision.Accept
        val delivery = accepted.delivery as ChatDelivery.Broadcast
        assertEquals(WORLD_ID, delivery.worldId)
        assertEquals(Topic.ofAlliance(ALLIANCE_ID), delivery.topic)
    }

    @Test
    fun `rejects alliance chat outside current alliance`() {
        val decision = policy().decideSend(
            participant(allianceId = ALLIANCE_ID),
            request(ChatChannel.Alliance, ALLIANCE_ID + 1),
        )

        val rejected = decision as ChatSendDecision.Reject
        assertEquals(ChatSendResult.ChatSendInvalidTarget, rejected.result)
        assertEquals("invalid alliance", rejected.reason)
    }

    @Test
    fun `resolves world and cross world topics`() {
        val world = policy().decideSend(participant(), request(ChatChannel.World))
        val crossWorld = policy().decideSend(participant(), request(ChatChannel.CrossWorld))

        val worldDelivery = (world as ChatSendDecision.Accept).delivery as ChatDelivery.Broadcast
        val crossWorldDelivery = (crossWorld as ChatSendDecision.Accept).delivery as ChatDelivery.Broadcast
        assertEquals(Topic.ofWorld(WORLD_ID), worldDelivery.topic)
        assertEquals(Topic.CROSS_WORLD_CHAT, crossWorldDelivery.topic)
    }

    @Test
    fun `rejects muted player`() {
        val now = 1_000L
        val decision = policy(now).decideSend(
            participant(mutedUntil = now + 1),
            request(ChatChannel.World),
        )

        val rejected = decision as ChatSendDecision.Reject
        assertEquals(ChatSendResult.ChatSendMuted, rejected.result)
        assertEquals(now + 1, rejected.mutedUntil)
    }

    @Test
    fun `rejects blocked keywords`() {
        val decision = policy().decideSend(participant(), request(ChatChannel.World, content = "badword"))

        val rejected = decision as ChatSendDecision.Reject
        assertEquals(ChatSendResult.ChatSendSensitiveContent, rejected.result)
    }

    @Test
    fun `rate limits accepted sends`() {
        var now = 1_000L
        val policy = DefaultChatPolicy(
            config = ChatPolicyConfig(
                rateLimitWindowMillis = 1_000,
                maxMessagesPerWindow = 2,
            ),
            nowMillis = { now },
        )

        policy.decideSend(participant(), request(ChatChannel.World))
        policy.decideSend(participant(), request(ChatChannel.World))
        val rejected = policy.decideSend(participant(), request(ChatChannel.World)) as ChatSendDecision.Reject

        assertEquals(ChatSendResult.ChatSendRateLimited, rejected.result)
        assertEquals(1_000, rejected.retryAfterMillis)

        now = 2_001L
        val accepted = policy.decideSend(participant(), request(ChatChannel.World))
        assertEquals(ChatSendDecision.Accept::class, accepted::class)
    }

    private fun policy(now: Long = 1_000L): ChatPolicy {
        return DefaultChatPolicy(nowMillis = { now })
    }

    private fun participant(
        playerId: Long = PLAYER_ID,
        worldId: Long = WORLD_ID,
        allianceId: Long = 0,
        mutedUntil: Long = 0,
    ): ChatParticipant {
        return ChatParticipant(
            playerId = playerId,
            worldId = worldId,
            allianceId = allianceId,
            mutedUntil = mutedUntil,
        )
    }

    private fun request(
        channel: ChatChannel,
        targetId: Long = 0,
        content: String = "hello",
        playerId: Long = PLAYER_ID,
    ): ChatSendReq {
        return ChatSendReq.newBuilder()
            .setPlayerId(playerId)
            .setChannel(channel)
            .setTargetId(targetId)
            .setContent(content)
            .build()
    }

    private companion object {
        const val PLAYER_ID = 1L
        const val WORLD_ID = 10L
        const val ALLIANCE_ID = 20L
    }
}
