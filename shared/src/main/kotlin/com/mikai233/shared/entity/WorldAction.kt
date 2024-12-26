package com.mikai233.shared.entity

import org.springframework.data.annotation.Id

data class WorldAction(
    @Id
    val id: String,
    val worldId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : TraceableRootEntity<Long> {
    override fun id(): Long {
        return worldId
    }
}
