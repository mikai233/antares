package com.mikai233.shared.entity

import com.mikai233.common.entity.ImmutableEntity
import org.springframework.data.annotation.Id

data class WorldUid(
    @Id
    val worldId: Long,
    val uidPrefix: Int
) : ImmutableEntity<Long> {
    override fun key(): Long {
        return worldId
    }
}
