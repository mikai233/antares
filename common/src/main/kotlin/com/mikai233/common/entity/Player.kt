package com.mikai233.common.entity

import com.mikai233.common.db.tracked.TrackEntity
import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "player")
data class Player(
    @Id
    override val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
) : Entity<Long> {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): Player {
            return Player(0, "", 0, "", 0)
        }
    }
}
