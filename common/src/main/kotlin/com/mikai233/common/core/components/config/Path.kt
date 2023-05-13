package com.mikai233.common.core.components.config

import com.mikai233.common.core.components.Role

const val ROOT = "/game_server"
const val NETTY_PATH = "$ROOT/netty"

fun serverHostsPath() = ServerHosts.PATH

fun serverHostPath(host: String) = "${ServerHosts.PATH}/$host"

fun nodePath(host: String, role: Role, port: Int) = "${serverHostPath(host)}/${role.name.lowercase()}:${port}"

fun nodePath(host: String, node: String) = "${serverHostPath(host)}/$node"

fun serverNetty(host: String) = "$NETTY_PATH/$host"
