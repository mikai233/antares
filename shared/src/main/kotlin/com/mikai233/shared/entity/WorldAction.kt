package com.mikai233.shared.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "world_action")
data class WorldAction(
    @Id
    val id: String,
    val worldId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): WorldAction {
            return WorldAction("", 0, 0, 0, 0)
        }
    }
}
