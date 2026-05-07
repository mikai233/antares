package com.mikai233.player.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.broadcast.Topic
import com.mikai233.common.entity.ChatMessageLog
import com.mikai233.common.entity.OfflinePrivateChatMessage
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.runtime.mongoDB
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerMem
import com.mikai233.protocol.ProtoChat
import com.mikai233.protocol.ProtoChat.ChatHistoryReq
import com.mikai233.protocol.ProtoChat.ChatHistoryResp
import com.mikai233.protocol.ProtoChat.ChatMessageNotify
import com.mikai233.protocol.ProtoChat.ChatSendReq
import com.mikai233.protocol.ProtoChat.ChatSendResp
import com.mikai233.protocol.ProtoChat.ChatSendResult
import com.mikai233.protocol.ProtoRpcChat.PlayerAllianceChangedReq
import com.mikai233.protocol.ProtoRpcChat.PrivateChatDeliverReq
import com.mikai233.protocol.ProtoRpcChat.WorldChatBroadcastReq
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldUnsubscribeTopicReq
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

@AllOpen
class ChatService {
    companion object {
        const val MaxContentLength = 200
        const val MaxOfflinePrivateMessagesPerLogin = 100
        const val RateLimitWindowMillis = 10_000L
        const val MaxMessagesPerWindow = 5
    }

    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val rateStates = ConcurrentHashMap<Long, PlayerChatRateState>()
    private val blockedKeywords = setOf(
        "badword",
    )

    fun handleSend(actor: PlayerActor, request: ChatSendReq) {
        val content = request.content.trim()
        when {
            request.playerId != actor.playerId -> {
                reject(actor, request, ChatSendResult.ChatSendRejected, "player_id mismatch")
            }

            content.isBlank() -> {
                reject(actor, request, ChatSendResult.ChatSendEmptyContent, "empty content")
            }

            content.length > MaxContentLength -> {
                reject(actor, request, ChatSendResult.ChatSendContentTooLong, "content too long")
            }

            mutedUntil(actor) > unixTimestamp() -> {
                reject(
                    actor,
                    request,
                    ChatSendResult.ChatSendMuted,
                    "player is muted",
                    mutedUntil = mutedUntil(actor),
                )
            }

            containsBlockedKeyword(content) -> {
                reject(actor, request, ChatSendResult.ChatSendSensitiveContent, "sensitive content")
            }

            retryAfterMillis(actor) > 0 -> {
                reject(
                    actor,
                    request,
                    ChatSendResult.ChatSendRateLimited,
                    "rate limited",
                    retryAfterMillis = retryAfterMillis(actor),
                )
            }

            else -> dispatch(actor, request, content)
        }
    }

    fun handleHistory(actor: PlayerActor, request: ChatHistoryReq) {
        if (request.playerId != actor.playerId) {
            actor.send(historyResp(request, emptyList()))
            return
        }
        actor.launch(timeout = null) {
            runCatching {
                val records = actor.node.mongoDB.reactiveTemplate.find(
                    historyQuery(actor, request),
                    ChatMessageLog::class.java,
                    ChatMessageLog.COLLECTION,
                ).collectList().awaitSingle()
                actor.send(historyResp(request, records.asReversed().map { it.toNotify() }))
            }.onFailure {
                logger.error("player:{} load chat history failed", actor.playerId, it)
                actor.send(historyResp(request, emptyList()))
            }
        }
    }

    private fun dispatch(actor: PlayerActor, request: ChatSendReq, content: String) {
        when (request.channel) {
            ProtoChat.ChatChannel.Private -> sendPrivate(actor, request, content)
            ProtoChat.ChatChannel.World -> {
                broadcastToWorld(actor, request, content, Topic.ofWorld(playerWorldId(actor)))
            }
            ProtoChat.ChatChannel.Alliance -> {
                val allianceId = playerAllianceId(actor)
                if (request.targetId <= 0 || request.targetId != allianceId) {
                    reject(actor, request, ChatSendResult.ChatSendInvalidTarget, "invalid alliance")
                } else {
                    broadcastToWorld(actor, request, content, Topic.ofAlliance(request.targetId))
                }
            }

            ProtoChat.ChatChannel.CrossWorld -> {
                broadcastToWorld(actor, request, content, Topic.CROSS_WORLD_CHAT)
            }

            ProtoChat.ChatChannel.System,
            ProtoChat.ChatChannel.ChatChannelUnspecified,
            ProtoChat.ChatChannel.UNRECOGNIZED,
            null,
                -> reject(actor, request, ChatSendResult.ChatSendInvalidChannel, "invalid channel")
        }
    }

    private fun sendPrivate(actor: PlayerActor, request: ChatSendReq, content: String) {
        if (request.targetId <= 0 || request.targetId == actor.playerId) {
            reject(actor, request, ChatSendResult.ChatSendInvalidTarget, "invalid private target")
            return
        }
        val notify = buildNotify(actor, request, content)
        persistChatLog(actor, notify)
        actor.tellPlayer(
            PrivateChatDeliverReq.newBuilder()
                .setPlayerId(request.targetId)
                .setMessage(notify)
                .build(),
        )
        actor.send(notify)
        actor.send(success(request, notify.messageId))
    }

    private fun broadcastToWorld(actor: PlayerActor, request: ChatSendReq, content: String, topic: String) {
        val notify = buildNotify(actor, request, content)
        persistChatLog(actor, notify)
        actor.tellWorld(
            WorldChatBroadcastReq.newBuilder()
                .setWorldId(playerWorldId(actor))
                .setTopic(topic)
                .setMessage(notify)
                .build(),
        )
        actor.send(success(request, notify.messageId))
    }

    fun deliverPrivate(actor: PlayerActor, request: PrivateChatDeliverReq) {
        if (actor.isOnline()) {
            actor.send(request.message)
        } else {
            persistOfflinePrivateMessage(actor, request.message)
        }
    }

    fun deliverOfflinePrivateMessages(actor: PlayerActor) {
        actor.launch(timeout = null) {
            runCatching {
                val template = actor.node.mongoDB.reactiveTemplate
                val loadQuery = query(where("targetPlayerId").`is`(actor.playerId))
                    .with(Sort.by(Sort.Direction.ASC, "sentAt"))
                    .limit(MaxOfflinePrivateMessagesPerLogin)
                val messages = template.find(
                    loadQuery,
                    OfflinePrivateChatMessage::class.java,
                    OfflinePrivateChatMessage.COLLECTION,
                ).collectList().awaitSingle()
                if (messages.isEmpty()) {
                    return@runCatching
                }
                messages.forEach { actor.send(it.toNotify()) }
                template.remove(
                    query(where("_id").`in`(messages.map { it.id })),
                    OfflinePrivateChatMessage::class.java,
                    OfflinePrivateChatMessage.COLLECTION,
                ).awaitSingle()
            }.onFailure {
                logger.error("player:{} deliver offline private chat messages failed", actor.playerId, it)
            }
        }
    }

    fun subscribeCurrentAllianceTopic(actor: PlayerActor) {
        val allianceId = playerAllianceId(actor)
        if (allianceId > 0) {
            subscribeAllianceTopic(actor, allianceId)
        }
    }

    fun handleAllianceChanged(actor: PlayerActor, request: PlayerAllianceChangedReq) {
        if (request.playerId != actor.playerId) {
            logger.warn("player:{} reject alliance change for player:{}", actor.playerId, request.playerId)
            return
        }
        val player = actor.manager.get<PlayerMem>().player
        val previousAllianceId = player.allianceId
        val nextAllianceId = request.allianceId.coerceAtLeast(0)
        if (previousAllianceId == nextAllianceId) {
            return
        }
        if (previousAllianceId > 0) {
            unsubscribeAllianceTopic(actor, previousAllianceId)
        }
        player.allianceId = nextAllianceId
        if (nextAllianceId > 0) {
            subscribeAllianceTopic(actor, nextAllianceId)
        }
    }

    fun setChatMutedUntil(actor: PlayerActor, mutedUntil: Long) {
        actor.manager.get<PlayerMem>().player.chatMutedUntil = mutedUntil.coerceAtLeast(0)
    }

    fun clearRateLimit(actor: PlayerActor) {
        rateStates.remove(actor.playerId)
    }

    private fun buildNotify(actor: PlayerActor, request: ChatSendReq, content: String): ChatMessageNotify {
        val player = actor.manager.get<PlayerMem>().player
        return ChatMessageNotify.newBuilder()
            .setMessageId(actor.nextId())
            .setChannel(request.channel)
            .setFromPlayerId(actor.playerId)
            .setFromName(player.nickname)
            .setTargetId(request.targetId)
            .setContent(content)
            .setSentAt(unixTimestamp())
            .setWorldId(player.worldId)
            .build()
    }

    private fun reject(
        actor: PlayerActor,
        request: ChatSendReq,
        result: ChatSendResult,
        reason: String,
        mutedUntil: Long = 0,
        retryAfterMillis: Long = 0,
    ) {
        actor.send(
            ChatSendResp.newBuilder()
                .setClientSeq(request.clientSeq)
                .setResult(result)
                .setReason(reason)
                .setMutedUntil(mutedUntil)
                .setRetryAfterMillis(retryAfterMillis)
                .build(),
        )
    }

    private fun success(request: ChatSendReq, messageId: Long): ChatSendResp {
        return ChatSendResp.newBuilder()
            .setClientSeq(request.clientSeq)
            .setMessageId(messageId)
            .setResult(ChatSendResult.ChatSendSuccess)
            .build()
    }

    private fun playerWorldId(actor: PlayerActor): Long {
        return actor.manager.get<PlayerMem>().player.worldId
    }

    private fun playerAllianceId(actor: PlayerActor): Long {
        return actor.manager.get<PlayerMem>().player.allianceId
    }

    private fun mutedUntil(actor: PlayerActor): Long {
        return actor.manager.get<PlayerMem>().player.chatMutedUntil
    }

    private fun containsBlockedKeyword(content: String): Boolean {
        val normalized = content.lowercase()
        return blockedKeywords.any(normalized::contains)
    }

    private fun retryAfterMillis(actor: PlayerActor): Long {
        val now = unixTimestamp()
        val state = rateStates.computeIfAbsent(actor.playerId) { PlayerChatRateState() }
        return state.hit(now)
    }

    private fun subscribeAllianceTopic(actor: PlayerActor, allianceId: Long) {
        actor.tellWorld(
            CrossWorldSubscribeTopicReq.newBuilder()
                .setWorldId(playerWorldId(actor))
                .setPlayerId(actor.playerId)
                .setTopic(Topic.ofAlliance(allianceId))
                .build(),
        )
    }

    private fun unsubscribeAllianceTopic(actor: PlayerActor, allianceId: Long) {
        actor.tellWorld(
            CrossWorldUnsubscribeTopicReq.newBuilder()
                .setWorldId(playerWorldId(actor))
                .setPlayerId(actor.playerId)
                .setTopic(Topic.ofAlliance(allianceId))
                .build(),
        )
    }

    private fun historyQuery(actor: PlayerActor, request: ChatHistoryReq): Query {
        val criteria = mutableListOf(
            where("channel").`is`(request.channelValue),
        )
        when (request.channel) {
            ProtoChat.ChatChannel.Private -> {
                require(request.targetId > 0) { "private chat history target_id must be positive" }
                criteria += Criteria().orOperator(
                    where("fromPlayerId").`is`(actor.playerId).and("targetId").`is`(request.targetId),
                    where("fromPlayerId").`is`(request.targetId).and("targetId").`is`(actor.playerId),
                )
            }

            ProtoChat.ChatChannel.World -> {
                criteria += where("worldId").`is`(playerWorldId(actor))
            }

            ProtoChat.ChatChannel.Alliance -> {
                require(request.targetId > 0) { "alliance chat history target_id must be positive" }
                require(request.targetId == playerAllianceId(actor)) { "player is not in alliance:${request.targetId}" }
                criteria += where("targetId").`is`(request.targetId)
            }

            ProtoChat.ChatChannel.CrossWorld -> Unit
            ProtoChat.ChatChannel.System,
            ProtoChat.ChatChannel.ChatChannelUnspecified,
            ProtoChat.ChatChannel.UNRECOGNIZED,
            null,
                -> error("unsupported chat history channel:${request.channel}")
        }
        if (request.beforeSentAt > 0) {
            criteria += where("sentAt").lt(request.beforeSentAt)
        }
        return query(Criteria().andOperator(criteria))
            .with(Sort.by(Sort.Direction.DESC, "sentAt"))
            .limit(request.limit.coerceIn(1, MaxOfflinePrivateMessagesPerLogin))
    }

    private fun persistChatLog(actor: PlayerActor, notify: ChatMessageNotify) {
        actor.launch(timeout = null) {
            runCatching {
                actor.node.mongoDB.reactiveTemplate.insert(
                    notify.toLog(),
                    ChatMessageLog.COLLECTION,
                ).awaitSingle()
            }.onFailure {
                logger.error("player:{} persist chat message:{} failed", actor.playerId, notify.messageId, it)
            }
        }
    }

    private fun persistOfflinePrivateMessage(actor: PlayerActor, notify: ChatMessageNotify) {
        actor.launch(timeout = null) {
            runCatching {
                actor.node.mongoDB.reactiveTemplate.insert(
                    notify.toOfflinePrivateMessage(actor.playerId),
                    OfflinePrivateChatMessage.COLLECTION,
                ).awaitSingle()
            }.onFailure {
                logger.error(
                    "player:{} persist offline private chat message:{} failed",
                    actor.playerId,
                    notify.messageId,
                    it,
                )
            }
        }
    }

    private fun ChatMessageNotify.toLog(): ChatMessageLog {
        return ChatMessageLog(
            id = messageId,
            channel = channelValue,
            fromPlayerId = fromPlayerId,
            fromName = fromName,
            targetId = targetId,
            content = content,
            sentAt = sentAt,
            worldId = worldId,
        )
    }

    private fun ChatMessageLog.toNotify(): ChatMessageNotify {
        return ChatMessageNotify.newBuilder()
            .setMessageId(id)
            .setChannel(ProtoChat.ChatChannel.forNumber(channel) ?: ProtoChat.ChatChannel.UNRECOGNIZED)
            .setFromPlayerId(fromPlayerId)
            .setFromName(fromName)
            .setTargetId(targetId)
            .setContent(content)
            .setSentAt(sentAt)
            .setWorldId(worldId)
            .build()
    }

    private fun historyResp(request: ChatHistoryReq, messages: List<ChatMessageNotify>): ChatHistoryResp {
        return ChatHistoryResp.newBuilder()
            .setChannel(request.channel)
            .setTargetId(request.targetId)
            .addAllMessages(messages)
            .build()
    }

    private fun ChatMessageNotify.toOfflinePrivateMessage(targetPlayerId: Long): OfflinePrivateChatMessage {
        return OfflinePrivateChatMessage(
            id = messageId,
            targetPlayerId = targetPlayerId,
            fromPlayerId = fromPlayerId,
            fromName = fromName,
            content = content,
            sentAt = sentAt,
            worldId = worldId,
        )
    }

    private fun OfflinePrivateChatMessage.toNotify(): ChatMessageNotify {
        return ChatMessageNotify.newBuilder()
            .setMessageId(id)
            .setChannel(ProtoChat.ChatChannel.Private)
            .setFromPlayerId(fromPlayerId)
            .setFromName(fromName)
            .setTargetId(targetPlayerId)
            .setContent(content)
            .setSentAt(sentAt)
            .setWorldId(worldId)
            .build()
    }

    private class PlayerChatRateState {
        private val timestamps = ArrayDeque<Long>()

        @Synchronized
        fun hit(now: Long): Long {
            while (timestamps.isNotEmpty() && now - timestamps.peekFirst() > RateLimitWindowMillis) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= MaxMessagesPerWindow) {
                return RateLimitWindowMillis - (now - timestamps.peekFirst())
            }
            timestamps.addLast(now)
            return 0
        }
    }
}
