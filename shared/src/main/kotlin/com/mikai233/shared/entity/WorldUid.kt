package com.mikai233.shared.entity

import org.springframework.data.annotation.Id

data class WorldUid(
    @Id
    val worldId: Long,
    val uidPrefix: Int
) : ImmutableEntity<Long> {
    override fun id(): Long {
        return worldId
    }
}
