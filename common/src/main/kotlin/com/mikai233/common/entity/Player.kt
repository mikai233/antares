package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "player")
@AsteriaMongoEntity(collection = "player", wrapperName = "PlayerTracked", helperName = "PlayerMongo")
data class Player(
    override val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
    var allianceId: Long,
    var chatMutedUntil: Long,
) : Entity<Long> {
    companion object {
        @JvmStatic
        fun defaults(): Player {
            return Player(0, "", 0, "", 0, 0, 0)
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: Long?,
            account: String?,
            worldId: Long?,
            nickname: String?,
            level: Int?,
            allianceId: Long?,
            chatMutedUntil: Long?,
        ): Player {
            val defaults = defaults()
            return Player(
                id = id ?: defaults.id,
                account = account ?: defaults.account,
                worldId = worldId ?: defaults.worldId,
                nickname = nickname ?: defaults.nickname,
                level = level ?: defaults.level,
                allianceId = allianceId ?: defaults.allianceId,
                chatMutedUntil = chatMutedUntil ?: defaults.chatMutedUntil,
            )
        }
    }
}
