package com.mikai233.gm.web

import com.mikai233.common.core.Patcher
import com.mikai233.common.serde.KryoPool
import com.mikai233.gm.GmNode
import com.mikai233.gm.web.plugins.*
import com.mikai233.gm.web.route.actorRoutes
import com.mikai233.gm.web.route.patchRoutes
import com.mikai233.gm.web.route.scriptRoutes
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import java.util.*

class Engine(private val node: GmNode) {
    private val environment = applicationEnvironment {
        config = HoconApplicationConfig(node.config)
    }
    private val server = embeddedServer(Netty, environment = environment, configure = {
        connector {
            host = environment.config.host
            port = environment.config.port
        }
    })

    fun start() {
        server.application.attributes.put(NodeKey, node)
        server.application.attributes.put(KryoKey, Patcher.scriptKryo())
        server.start(false)
    }
}

val NodeKey = AttributeKey<GmNode>("Node")

val KryoKey = AttributeKey<KryoPool>("Kryo")

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureRouting()
    configureStatusPage()
    configureValidation()
    scriptRoutes()
    patchRoutes()
    actorRoutes()
}

fun Application.node() = attributes[NodeKey]

fun Application.kryo() = attributes[KryoKey]

fun uuid(): String = UUID.randomUUID().toString()