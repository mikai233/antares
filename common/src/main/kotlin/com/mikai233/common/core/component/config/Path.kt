package com.mikai233.common.core.component.config

import com.mikai233.common.core.component.Role

const val ROOT = "/antares"
const val NETTY_PATH = "$ROOT/netty"
const val GAME_WORLD = "$ROOT/game_worlds"

fun serverHostsPath() = ServerHosts.PATH

fun serverHostPath(host: String) = "${ServerHosts.PATH}/$host"

fun nodePath(host: String, role: Role, port: Int) = "${serverHostPath(host)}/${role.name.lowercase()}:${port}"

fun nodePath(host: String, node: String) = "${serverHostPath(host)}/$node"

fun serverNetty(host: String) = "$NETTY_PATH/$host"

fun gameWorldPath(worldId: String) = "$GAME_WORLD/$worldId"

fun excelPath() = "$ROOT/excel"

fun excelVersion(version: String) = "${excelPath()}/$version"

fun excelFile(version: String, name: String) = "${excelVersion(version)}/$name"
