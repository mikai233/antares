package com.mikai233.common.runtime.module

import com.mikai233.common.time.GameTimeSource
import com.typesafe.config.Config
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.milliseconds

class GameTimeModule(
    private val config: Config,
) : AsteriaModule {
    override val name: String = "game-time"

    override suspend fun install(context: ModuleContext) {
        context.services.register(
            GameTimeSource::class,
            GameTimeSource(
                timeZone = TimeZone.of(config.stringOrDefault("game.time.zone", "Asia/Shanghai")),
                initialGlobalOffset = config.durationMillisOrDefault("game.time.global-offset", 0).milliseconds,
            ),
        )
    }
}

private fun Config.stringOrDefault(path: String, defaultValue: String): String {
    return if (hasPath(path)) getString(path) else defaultValue
}

private fun Config.durationMillisOrDefault(path: String, defaultValue: Long): Long {
    return if (hasPath(path)) getDuration(path).toMillis() else defaultValue
}
