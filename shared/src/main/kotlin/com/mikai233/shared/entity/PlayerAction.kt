package com.mikai233.shared.entity

import com.mikai233.common.entity.TraceableRootEntity
import org.springframework.data.annotation.Id

data class PlayerAction(
    @Id
    val id: String,
    val playerId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : TraceableRootEntity<Long> {
    override fun key(): Long {
        return playerId
    }
}

