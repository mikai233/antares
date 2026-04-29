package com.mikai233.common.entity

import com.mikai233.common.db.Entity
import com.mikai233.common.db.tracked.TrackEntity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "player_action")
data class PlayerAction(
    @Id
    val id: String,
    val playerId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): PlayerAction {
            return PlayerAction("", 0, 0, 0, 0)
        }
    }
}
