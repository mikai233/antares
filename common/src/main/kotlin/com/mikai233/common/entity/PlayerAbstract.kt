package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoIgnore
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "player_abstract")
@AsteriaMongoEntity(
    collection = "player_abstract",
    wrapperName = "PlayerAbstractTracked",
    helperName = "PlayerAbstractMongo",
)
data class PlayerAbstract(
    override val id: Long,
    val worldId: Long,
    val account: String,
    var nickname: String,
    var level: Int,
    val createTime: Long,
) : Entity<Long> {
    @AsteriaMongoIgnore
    val playerId: Long
        get() = id

    companion object {
        @JvmStatic
        fun defaults(): PlayerAbstract {
            return PlayerAbstract(0, 0, "", "", 0, 0)
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: Long?,
            worldId: Long?,
            account: String?,
            nickname: String?,
            level: Int?,
            createTime: Long?,
        ): PlayerAbstract {
            val defaults = defaults()
            return PlayerAbstract(
                id = id ?: defaults.id,
                worldId = worldId ?: defaults.worldId,
                account = account ?: defaults.account,
                nickname = nickname ?: defaults.nickname,
                level = level ?: defaults.level,
                createTime = createTime ?: defaults.createTime,
            )
        }
    }
}
