package com.mikai233.shared.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id

data class Player(
    @Id
    val id: Long,
    val account: String,
    val worldId: Long,
    var nickname: String,
    var level: Int,
) : Entity
