package com.mikai233.common.core.components.config

import com.mikai233.common.core.components.Role


/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/11
 */
class ServerHosts(val systemName: String) : Config {
    companion object {
        const val PATH = "$ROOT/server_hosts"
    }

    override fun path(): String {
        return PATH
    }
}

data class Node(val host: String, val role: Role, val port: Int, val seed: Boolean) : Config {
    override fun path(): String {
        return nodePath(host, role, port)
    }
}