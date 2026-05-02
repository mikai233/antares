package com.mikai233.common.entity

import io.github.mikai233.asteria.persistence.Entity
import io.github.mikai233.asteria.persistence.AsteriaMongoEntity
import io.github.mikai233.asteria.persistence.AsteriaMongoId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@AsteriaMongoEntity(collection = "world_action", wrapperName = "WorldActionTracked", helperName = "WorldActionMongo")
@Document(collection = "world_action")
data class WorldAction(
    @Id
    @AsteriaMongoId
    override val id: String,
    val worldId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity<String> {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): WorldAction {
            return WorldAction("", 0, 0, 0, 0)
        }
    }
}
