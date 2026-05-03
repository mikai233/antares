package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity

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
        fun create(): WorldAction {
            return WorldAction("", 0, 0, 0, 0)
        }
    }
}
