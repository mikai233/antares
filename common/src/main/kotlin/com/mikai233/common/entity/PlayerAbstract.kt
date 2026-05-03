package com.mikai233.common.entity

import io.github.mikai233.asteria.persistence.Entity
import io.github.mikai233.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import io.github.mikai233.asteria.persistence.mongodb.annotations.AsteriaMongoId
import io.github.mikai233.asteria.persistence.mongodb.annotations.AsteriaMongoIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@AsteriaMongoEntity(
    collection = "player_abstract",
    wrapperName = "PlayerAbstractTracked",
    helperName = "PlayerAbstractMongo",
)
@Document(collection = "player_abstract")
data class PlayerAbstract(
    @Id
    @AsteriaMongoId
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
        @PersistenceCreator
        fun create(): PlayerAbstract {
            return PlayerAbstract(0, 0, "", "", 0, 0)
        }
    }
}
