package com.mikai233.common.entity

import com.mikai233.common.db.Entity
import com.mikai233.common.db.tracked.TrackEntity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "player")
data class Player(
    @Id
    val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): Player {
            return Player(0, "", 0, "", 0)
        }
    }
}
