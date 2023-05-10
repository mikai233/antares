package com.mikai233.core.components

import akka.actor.typed.ActorSystem
import com.mikai233.core.Server
import com.mikai233.ext.logger

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */
enum class Role {
    Home,
    Gate,
}

class Cluster<T>(val role: Role, val host: String, val port: UShort) : Component {
    val logger = logger()

    lateinit var system: ActorSystem<T>

    override fun init(server: Server) {
        TODO("Not yet implemented")
    }

    override fun shutdown(server: Server) {
        TODO("Not yet implemented")
    }
}