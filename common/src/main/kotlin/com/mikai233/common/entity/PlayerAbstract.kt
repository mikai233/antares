package com.mikai233.common.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id

data class PlayerAbstract(
    @Id
    val playerId: Long,
    val worldId: Long,
    val account: String,
    var nickname: String,
    var level: Int,
    val createTime: Long,
) : Entity
