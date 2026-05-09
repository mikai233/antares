package com.mikai233.player.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.broadcast.Topic
import com.mikai233.common.runtime.mongoDB
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerMem
import com.mikai233.player.entity.ChatMessageLog
import com.mikai233.player.entity.OfflinePrivateChatMessage
import com.mikai233.player.service.chat.*
import com.mikai233.protocol.ProtoChat.*
import com.mikai233.protocol.ProtoRpcChat.*
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldUnsubscribeTopicReq
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query

@AllOpen
class ChatService(
    private val chatPolicy: ChatPolicy = DefaultChatPolicy(
        ChatPolicyConfig(
            maxContentLength = MAX_CONTENT_LENGTH,
            maxOfflinePrivateMessagesPerLogin = MAX_OFFLINE_PRIVATE_MESSAGES_PER_LOGIN,
            rateLimitWindowMillis = RATE_LIMIT_WINDOW_MILLIS,
            maxMessagesPerWindow = MAX_MESSAGES_PER_WINDOW,
        ),
    ),
) {
    companion object {
        const val MAX_CONTENT_LENGTH = 200
        const val MAX_OFFLINE_PRIVATE_MESSAGES_PER_LOGIN = 100
        const val RATE_LIMIT_WINDOW_MILLIS = 10_000L
        const val MAX_MESSAGES_PER_WINDOW = 5
    }

    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    fun handleSend(actor: PlayerActor, request: ChatSendReq) {
        when (val decision = chatPolicy.decideSend(actor.toChatParticipant(), request, actor.gameTime.nowMillis())) {
            is ChatSendDecision.Accept -> {
                dispatch(actor, request, decision)
            }

            is ChatSendDecision.Reject -> {
                reject(
                    actor,
                    request,
                    decision.result,
                    decision.reason,
                    mutedUntil = decision.mutedUntil,
                    retryAfterMillis = decision.retryAfterMillis,
                )
            }
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

    private fun dispatch(actor: PlayerActor, request: ChatSendReq, decision: ChatSendDecision.Accept) {
        when (val delivery = decision.delivery) {
            is ChatDelivery.Private -> {
                sendPrivate(actor, request, decision.content, delivery.targetPlayerId)
            }

            is ChatDelivery.Broadcast -> {
                broadcastToWorld(actor, request, decision.content, delivery.worldId, delivery.topic)
            }
        }
    }

    private fun sendPrivate(actor: PlayerActor, request: ChatSendReq, content: String, targetPlayerId: Long) {
        val notify = buildNotify(actor, request, content)
        persistChatLog(actor, notify)
        actor.tellPlayer(
            PrivateChatDeliverReq.newBuilder()
                .setPlayerId(targetPlayerId)
                .setMessage(notify)
                .build(),
        )
        actor.send(notify)
        actor.send(success(request, notify.messageId))
    }

    private fun broadcastToWorld(
        actor: PlayerActor,
        request: ChatSendReq,
        content: String,
        worldId: Long,
        topic: String,
    ) {
        val notify = buildNotify(actor, request, content)
        persistChatLog(actor, notify)
        actor.tellWorld(
            WorldChatBroadcastReq.newBuilder()
                .setWorldId(worldId)
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
                    .limit(chatPolicy.maxOfflinePrivateMessagesPerLogin)
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
        chatPolicy.clearRateLimit(actor.playerId)
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
            .setSentAt(actor.gameTime.nowMillis())
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
            ChatChannel.Private -> {
                require(request.targetId > 0) { "private chat history target_id must be positive" }
                criteria += Criteria().orOperator(
                    where("fromPlayerId").`is`(actor.playerId).and("targetId").`is`(request.targetId),
                    where("fromPlayerId").`is`(request.targetId).and("targetId").`is`(actor.playerId),
                )
            }

            ChatChannel.World -> {
                criteria += where("worldId").`is`(playerWorldId(actor))
            }

            ChatChannel.Alliance -> {
                require(request.targetId > 0) { "alliance chat history target_id must be positive" }
                require(request.targetId == playerAllianceId(actor)) { "player is not in alliance:${request.targetId}" }
                criteria += where("targetId").`is`(request.targetId)
            }

            ChatChannel.CrossWorld -> Unit
            ChatChannel.System,
            ChatChannel.ChatChannelUnspecified,
            ChatChannel.UNRECOGNIZED,
            null,
                -> error("unsupported chat history channel:${request.channel}")
        }
        if (request.beforeSentAt > 0) {
            criteria += where("sentAt").lt(request.beforeSentAt)
        }
        return query(Criteria().andOperator(criteria))
            .with(Sort.by(Sort.Direction.DESC, "sentAt"))
            .limit(request.limit.coerceIn(1, chatPolicy.maxOfflinePrivateMessagesPerLogin))
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
            .setChannel(ChatChannel.forNumber(channel) ?: ChatChannel.UNRECOGNIZED)
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
            .setChannel(ChatChannel.Private)
            .setFromPlayerId(fromPlayerId)
            .setFromName(fromName)
            .setTargetId(targetPlayerId)
            .setContent(content)
            .setSentAt(sentAt)
            .setWorldId(worldId)
            .build()
    }

    private fun PlayerActor.toChatParticipant(): ChatParticipant {
        val player = manager.get<PlayerMem>().player
        return ChatParticipant(
            playerId = playerId,
            worldId = player.worldId,
            allianceId = player.allianceId,
            mutedUntil = player.chatMutedUntil,
        )
    }
}
