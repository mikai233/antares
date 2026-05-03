package com.mikai233.common.entity

import io.github.mikai233.asteria.persistence.Entity
import io.github.mikai233.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import io.github.mikai233.asteria.persistence.mongodb.annotations.AsteriaMongoId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@AsteriaMongoEntity(collection = "player", wrapperName = "PlayerTracked", helperName = "PlayerMongo")
@Document(collection = "player")
data class Player(
    @Id
    @AsteriaMongoId
    override val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
) : Entity<Long> {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): Player {
            return Player(0, "", 0, "", 0)
        }
    }
}
