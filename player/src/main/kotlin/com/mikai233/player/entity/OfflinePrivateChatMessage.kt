package com.mikai233.player.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = OfflinePrivateChatMessage.COLLECTION)
data class OfflinePrivateChatMessage(
    @Id
    val id: Long,
    val targetPlayerId: Long,
    val fromPlayerId: Long,
    val fromName: String,
    val content: String,
    val sentAt: Long,
    val worldId: Long,
) {
    companion object {
        const val COLLECTION = "offline_private_chat_message"

        @JvmStatic
        fun defaults(): OfflinePrivateChatMessage {
            return OfflinePrivateChatMessage(0, 0, 0, "", "", 0, 0)
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: Long?,
            targetPlayerId: Long?,
            fromPlayerId: Long?,
            fromName: String?,
            content: String?,
            sentAt: Long?,
            worldId: Long?,
        ): OfflinePrivateChatMessage {
            val defaults = defaults()
            return OfflinePrivateChatMessage(
                id = id ?: defaults.id,
                targetPlayerId = targetPlayerId ?: defaults.targetPlayerId,
                fromPlayerId = fromPlayerId ?: defaults.fromPlayerId,
                fromName = fromName ?: defaults.fromName,
                content = content ?: defaults.content,
                sentAt = sentAt ?: defaults.sentAt,
                worldId = worldId ?: defaults.worldId,
            )
        }
    }
}
