package com.mikai233.common.entity

import com.mikai233.common.db.tracked.TrackEntity
import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "world_action")
data class WorldAction(
    @Id
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
