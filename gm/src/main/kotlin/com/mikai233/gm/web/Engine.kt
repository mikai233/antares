package com.mikai233.gm.web

import com.mikai233.common.extension.logger
import com.mikai233.gm.GmNode
import com.mikai233.gm.web.plugins.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class Engine(private val node: GmNode) {
    private val logger = logger()
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
        server.start(false)
    }
}

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureRouting()
    configureStatusPage()
    configureClusterHeader()
    configureValidation()
    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
    }
}
