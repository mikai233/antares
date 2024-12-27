package com.mikai233.shared.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id

data class WorldUid(
    @Id
    val worldId: Long,
    val uidPrefix: Int
) : Entity
