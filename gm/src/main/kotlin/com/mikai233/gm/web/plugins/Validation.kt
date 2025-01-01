package com.mikai233.gm.web.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class ValidateException(message: String) : Exception(message)

@OptIn(ExperimentalContracts::class)
fun <T : Any> notNull(value: T?, lazyMessage: () -> Any): T {
    contract {
        returns() implies (value != null)
    }
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