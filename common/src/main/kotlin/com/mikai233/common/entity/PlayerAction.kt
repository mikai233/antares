package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import org.springframework.data.annotation.PersistenceCreator

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
        fun defaults(): PlayerAction {
            return PlayerAction("", 0, 0, 0, 0)
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: String?,
            playerId: Long?,
            actionId: Int?,
            latestActionMills: Long?,
            actionParam: Long?,
        ): PlayerAction {
            val defaults = defaults()
            return PlayerAction(
                id = id ?: defaults.id,
                playerId = playerId ?: defaults.playerId,
                actionId = actionId ?: defaults.actionId,
                latestActionMills = latestActionMills ?: defaults.latestActionMills,
                actionParam = actionParam ?: defaults.actionParam,
            )
        }
    }
}
