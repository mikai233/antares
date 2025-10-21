package com.mikai233.common.config

import com.mikai233.common.core.Role

data class NodeConfig(
    val role: Role,
    val port: Int,
    val seed: Boolean,
)
