package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity

@AsteriaMongoEntity(collection = "player", wrapperName = "PlayerTracked", helperName = "PlayerMongo")
data class Player(
    override val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
) : Entity<Long> {
    companion object {
        @JvmStatic
        fun create(): Player {
            return Player(0, "", 0, "", 0)
        }
    }
}
