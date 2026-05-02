package com.mikai233.common.entity

import com.mikai233.common.db.tracked.TrackEntity
import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "player_action")
data class PlayerAction(
    @Id
    override val id: String,
    val playerId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity<String> {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): PlayerAction {
            return PlayerAction("", 0, 0, 0, 0)
        }
    }
}
