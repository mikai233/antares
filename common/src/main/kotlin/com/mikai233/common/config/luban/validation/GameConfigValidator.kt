package com.mikai233.common.config.luban.validation

import io.github.realmlabs.asteria.config.ConfigSnapshot

fun interface GameConfigValidator {
    fun validate(snapshot: ConfigSnapshot)
}

object GameConfigValidators {
    val defaultValidators: List<GameConfigValidator> = listOf(
        MonsterConfigValidator,
        DropPoolConfigValidator,
    )
}
