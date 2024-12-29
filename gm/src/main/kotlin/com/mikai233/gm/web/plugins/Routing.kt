package com.mikai233.gm.web.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        staticResources("/", "static/web") {
            cacheControl {
                listOf(CacheControl.MaxAge(60))
            }
        }
    }
}
