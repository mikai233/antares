package com.mikai233.shared.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id

data class PlayerAction(
    @Id
    val id: String,
    val playerId: Long,
    val actionId: Int,
    var latestActionMills: Long,
    var actionParam: Long,
) : Entity

