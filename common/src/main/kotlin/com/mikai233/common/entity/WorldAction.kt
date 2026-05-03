package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import org.springframework.data.annotation.PersistenceCreator

@AsteriaMongoEntity(collection = "world_action", wrapperName = "WorldActionTracked", helperName = "WorldActionMongo")
data class WorldAction(
    override val id: String,
    val worldId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity<String> {
    companion object {
        @JvmStatic
        fun defaults(): WorldAction {
            return WorldAction("", 0, 0, 0, 0)
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: String?,
            worldId: Long?,
            actionId: Int?,
            latestActionMills: Long?,
            actionParam: Long?,
        ): WorldAction {
            val defaults = defaults()
            return WorldAction(
                id = id ?: defaults.id,
                worldId = worldId ?: defaults.worldId,
                actionId = actionId ?: defaults.actionId,
                latestActionMills = latestActionMills ?: defaults.latestActionMills,
                actionParam = actionParam ?: defaults.actionParam,
            )
        }
    }
}
