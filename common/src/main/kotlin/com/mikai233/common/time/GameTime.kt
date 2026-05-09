package com.mikai233.common.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

interface GameTime : Clock {
    val timeZone: TimeZone

    fun nowMillis(): Long = now().toEpochMilliseconds()

    fun nowLocal(): LocalDateTime = now().toLocalDateTime(timeZone)

    fun today(): LocalDate = nowLocal().date

    fun globalOffset(): Duration

    fun actorOffset(): Duration

    fun totalOffset(): Duration
}
