package com.mikai233.common.core.config

const val SYSTEM_NAME = "antares"

const val Root = "/$SYSTEM_NAME"

const val ServerHosts = "$Root/server_hosts"

fun serverHostsPath(hostname: String) = "$ServerHosts/$hostname"

fun nodePath(hostname: String, nodeName: String) = "${serverHostsPath(hostname)}/$nodeName"
