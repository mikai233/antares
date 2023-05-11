package com.mikai233.core.components.config

import com.mikai233.core.components.Role

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

data class Host(val address: String) : Config {

    override fun path(): String {
        return "${ServerHosts.PATH}/$address"
    }
}

data class Node(val host: Host, val role: Role, val port: Int, val seed: Boolean) :
    Config {
    override fun path(): String {
        return "${host.path()}/${role.name.lowercase()}:${port}"
    }
}