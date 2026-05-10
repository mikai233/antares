package com.mikai233.common.extension

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger(removeCompanion: Boolean = true): Logger {
    return if (T::class.isCompanion && removeCompanion)
        LoggerFactory.getLogger(T::class.qualifiedName?.removeSuffix(".Companion") ?: T::class.java.name)
    else LoggerFactory.getLogger(T::class.java)
}
