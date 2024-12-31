package com.mikai233.shared.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator

data class WorldUid(
    @Id
    val worldId: Long,
    val uidPrefix: Int
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): WorldUid {
            return WorldUid(0, 0)
        }
    }
}
