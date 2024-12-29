package com.mikai233.gm.web.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPage() {
    install(StatusPages) {
        exception<MissingClusterHeaderException> { call, cause ->
            call.respondText("Error: ${cause.localizedMessage}", status = HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            call.respondText("Error: ${cause.localizedMessage}", status = HttpStatusCode.InternalServerError)
        }
    }
}