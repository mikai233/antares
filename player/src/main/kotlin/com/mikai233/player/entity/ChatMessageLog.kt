package com.mikai233.player.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = ChatMessageLog.COLLECTION)
data class ChatMessageLog(
    @Id
    val id: Long,
    val channel: Int,
    val fromPlayerId: Long,
    val fromName: String,
    val targetId: Long,
    val content: String,
    val sentAt: Long,
    val worldId: Long,
) {
    companion object {
        const val COLLECTION = "chat_message_log"

        @JvmStatic
        fun defaults(): ChatMessageLog {
            return ChatMessageLog(0, 0, 0, "", 0, "", 0, 0)
        }

        @JvmStatic
        @PersistenceCreator
        @Suppress("LongParameterList")
        fun create(
            id: Long?,
            channel: Int?,
            fromPlayerId: Long?,
            fromName: String?,
            targetId: Long?,
            content: String?,
            sentAt: Long?,
            worldId: Long?,
        ): ChatMessageLog {
            val defaults = defaults()
            return ChatMessageLog(
                id = id ?: defaults.id,
                channel = channel ?: defaults.channel,
                fromPlayerId = fromPlayerId ?: defaults.fromPlayerId,
                fromName = fromName ?: defaults.fromName,
                targetId = targetId ?: defaults.targetId,
                content = content ?: defaults.content,
                sentAt = sentAt ?: defaults.sentAt,
                worldId = worldId ?: defaults.worldId,
            )
        }
    }
}
