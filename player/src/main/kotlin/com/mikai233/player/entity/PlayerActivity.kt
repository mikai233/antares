package com.mikai233.player.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "player_activity")
@AsteriaMongoEntity(
    collection = "player_activity",
    wrapperName = "PlayerActivityTracked",
    helperName = "PlayerActivityMongo",
)
data class PlayerActivity(
    override val id: String,
    val playerId: Long,
    val activityId: String,
    var activityName: String,
    var unlockLevel: Int,
    var conditionSummary: String,
    var rewardSummary: String,
) : Entity<String> {
    companion object {
        @JvmStatic
        fun defaults(): PlayerActivity {
            return PlayerActivity("", 0L, "", "", 0, "", "")
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: String?,
            playerId: Long?,
            activityId: String?,
            activityName: String?,
            unlockLevel: Int?,
            conditionSummary: String?,
            rewardSummary: String?,
        ): PlayerActivity {
            val defaults = defaults()
            return PlayerActivity(
                id = id ?: defaults.id,
                playerId = playerId ?: defaults.playerId,
                activityId = activityId ?: defaults.activityId,
                activityName = activityName ?: defaults.activityName,
                unlockLevel = unlockLevel ?: defaults.unlockLevel,
                conditionSummary = conditionSummary ?: defaults.conditionSummary,
                rewardSummary = rewardSummary ?: defaults.rewardSummary,
            )
        }
    }
}
