@file:Suppress("MatchingDeclarationName")

package com.mikai233.gm.web.support

class ValidateException(message: String) : RuntimeException(message)

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
