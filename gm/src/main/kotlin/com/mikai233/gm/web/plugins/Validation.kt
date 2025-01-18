@file:Suppress("MatchingDeclarationName")

package com.mikai233.gm.web.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

class ValidateException(message: String) : Exception(message)

fun <T : Any> notNull(value: T?, lazyMessage: () -> Any): T {
    if (value == null) {
        throw ValidateException(lazyMessage().toString())
    }
    return value
}

fun ensure(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        throw ValidateException(lazyMessage().toString())
    }
}

fun Application.configureValidation() {
    install(RequestValidation) {

    }
}
