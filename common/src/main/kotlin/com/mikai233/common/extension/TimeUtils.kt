package com.mikai233.common.extension

import com.mikai233.common.conf.GlobalEnv
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

fun timestampToLocalDateTime(value: Long) = Instant.fromEpochMilliseconds(value).toLocalDateTime(GlobalEnv.zoneId)
