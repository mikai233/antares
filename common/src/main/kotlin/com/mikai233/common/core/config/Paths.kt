package com.mikai233.common.core.config

const val SystemName = "antares"

const val Root = "/$SystemName"

const val ServerHosts = "$Root/server_hosts"

const val DataSourceGame = "$Root/data_source/game"

fun serverHostsPath(hostname: String) = "$ServerHosts/$hostname"

fun nodePath(hostname: String, nodeName: String) = "${serverHostsPath(hostname)}/$nodeName"
