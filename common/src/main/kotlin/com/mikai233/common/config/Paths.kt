package com.mikai233.common.config

const val SYSTEM_NAME = "antares"

const val ROOT = "/$SYSTEM_NAME"

const val SERVER_HOSTS = "$ROOT/server_hosts"

const val DATA_SOURCE_GAME = "$ROOT/data_source/game"

const val GAME_WORLDS = "$ROOT/game_worlds"

const val GAME_CONFIG = "$ROOT/game_config"

const val PATCH = "$ROOT/patch"

fun serverHostsPath(hostname: String) = "$SERVER_HOSTS/$hostname"

fun nodePath(hostname: String, nodeName: String) = "${serverHostsPath(hostname)}/$nodeName"

fun nettyConfigPath(hostname: String, port: Int) = "$ROOT/netty/$hostname:$port"

fun patchByVersion(version: String) = "$PATCH/$version"
