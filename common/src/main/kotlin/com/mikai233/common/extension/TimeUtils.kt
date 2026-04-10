package com.mikai233.common.extension

import com.mikai233.common.conf.GlobalEnv
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun timestampToLocalDateTime(value: Long) = Instant.fromEpochMilliseconds(value).toLocalDateTime(GlobalEnv.zoneId)
