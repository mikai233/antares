package com.mikai233.common.entity

import org.springframework.data.annotation.Id

data class PlayerAbstract(
    @Id
    val playerId: Long,
    val worldId: Long,
    val account: String,
    var nickname: String,
    var level: Int,
    val createTime: Long,
) : TraceableFieldEntity<Long> {
    override fun key(): Long {
        return playerId
    }
}
