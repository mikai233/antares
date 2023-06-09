package com.mikai233.shared.entity

import com.mikai233.common.entity.TraceableRootEntity
import org.springframework.data.annotation.Id

data class WorldAction(
    @Id
    val id: String,
    val worldId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : TraceableRootEntity<Long> {
    override fun key(): Long {
        return worldId
    }
}
