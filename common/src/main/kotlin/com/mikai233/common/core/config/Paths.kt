package com.mikai233.common.core.config

const val SYSTEM_NAME = "antares"

const val ROOT = "/$SYSTEM_NAME"

const val SERVER_HOSTS = "$ROOT/server_hosts"

const val DATA_SOURCE_GAME = "$ROOT/data_source/game"

fun serverHostsPath(hostname: String) = "$SERVER_HOSTS/$hostname"

fun nodePath(hostname: String, nodeName: String) = "${serverHostsPath(hostname)}/$nodeName"

fun nettyConfigPath(hostname: String) = "$ROOT/netty/$hostname"
