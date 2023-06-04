package com.mikai233.shared.entity

import com.mikai233.common.entity.TraceableRootEntity
import org.springframework.data.annotation.Id

data class WorldAction(@Id val worldId: Long, var actionTime: Long, var param: Long) : TraceableRootEntity<Long> {
    override fun key(): Long {
        return worldId
    }
}
