package com.mikai233.common.core.component.config

import com.mikai233.common.core.component.Role

data class TestConfig(
    val role: Role,
    val port: Int,
    val seed: Boolean,
)
