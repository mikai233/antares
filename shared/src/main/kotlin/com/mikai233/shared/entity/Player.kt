package com.mikai233.shared.entity

import com.mikai233.common.entity.TraceableFieldEntity
import org.springframework.data.annotation.Id

data class Player(
    @Id
    val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
) : TraceableFieldEntity<Long> {
    override fun key(): Long {
        return id
    }
}
