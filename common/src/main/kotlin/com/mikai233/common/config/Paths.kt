package com.mikai233.common.config

import io.github.mikai233.asteria.config.center.ConfigPath

const val SYSTEM_NAME = "antares"

val ROOT: ConfigPath = ConfigPath.Root / SYSTEM_NAME

val DATA_SOURCE_GAME: ConfigPath = ROOT / "data-source" / "game"

val GAME_WORLDS: ConfigPath = ROOT / "game-worlds"

val GAME_CONFIG_PUBLICATION: ConfigPath = ROOT / "game-config"

const val WORKER_IDS = "/$SYSTEM_NAME/worker-ids"

val NETTY_CONFIGS: ConfigPath = ROOT / "netty"

fun nettyConfigPath(nodeId: String): ConfigPath = NETTY_CONFIGS / nodeId
