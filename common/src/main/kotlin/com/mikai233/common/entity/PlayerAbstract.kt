package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoIgnore

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
        fun create(): PlayerAbstract {
            return PlayerAbstract(0, 0, "", "", 0, 0)
        }
    }
}
