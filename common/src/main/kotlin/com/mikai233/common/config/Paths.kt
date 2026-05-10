package com.mikai233.common.config

import io.github.realmlabs.asteria.config.center.ConfigPath

const val SYSTEM_NAME = "antares"

val ROOT: ConfigPath = ConfigPath.Root / SYSTEM_NAME

val DATA_SOURCE_GAME: ConfigPath = ROOT / "data-source" / "game"

val GAME_WORLDS: ConfigPath = ROOT / "game-worlds"

val GAME_CONFIG_PUBLICATION: ConfigPath = ROOT / "game-config"

val GAME_TIME: ConfigPath = ROOT / "game-time"

val GAME_TIME_OVERRIDE: ConfigPath = GAME_TIME / "override"

val GAME_TIME_RELOAD_ACKS: ConfigPath = ROOT / "game-time-reload-acks"

val BATTLE_INSTANCES: ConfigPath = ROOT / "battle" / "instances"

const val WORKER_IDS = "/$SYSTEM_NAME/worker-ids"

val NETTY_CONFIGS: ConfigPath = ROOT / "netty"

fun nettyConfigPath(nodeId: String): ConfigPath = NETTY_CONFIGS / nodeId

fun gameTimeReloadAckPath(epoch: Long): ConfigPath = GAME_TIME_RELOAD_ACKS / epoch.toString()

fun gameTimeReloadAckPath(epoch: Long, nodeId: String): ConfigPath = gameTimeReloadAckPath(epoch) / nodeId
