package com.mikai233.common.entity

import com.mikai233.common.db.tracked.TrackEntity
import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "player_abstract")
data class PlayerAbstract(
    @Id
    val playerId: Long,
    val worldId: Long,
    val account: String,
    var nickname: String,
    var level: Int,
    val createTime: Long,
) : Entity<Long> {
    override val id: Long
        get() = playerId

    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): PlayerAbstract {
            return PlayerAbstract(0, 0, "", "", 0, 0)
        }
    }
}
