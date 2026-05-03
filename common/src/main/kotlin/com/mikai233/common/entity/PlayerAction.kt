package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity

@AsteriaMongoEntity(collection = "player_action", wrapperName = "PlayerActionTracked", helperName = "PlayerActionMongo")
data class PlayerAction(
    override val id: String,
    val playerId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity<String> {
    companion object {
        @JvmStatic
        fun create(): PlayerAction {
            return PlayerAction("", 0, 0, 0, 0)
        }
    }
}
