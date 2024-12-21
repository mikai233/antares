package com.mikai233.common.extension

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */
inline fun <reified T> T.logger(removeCompanion: Boolean = true): Logger {
    return if (T::class.isCompanion && removeCompanion)
        LoggerFactory.getLogger(T::class.qualifiedName?.removeSuffix(".Companion") ?: T::class.java.name)
    else LoggerFactory.getLogger(T::class.java)
}
