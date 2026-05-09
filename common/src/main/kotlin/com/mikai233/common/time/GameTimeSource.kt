package com.mikai233.common.time

import kotlinx.datetime.TimeZone
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class GameTimeSource(
    override val timeZone: TimeZone,
    initialGlobalOffset: Duration = Duration.ZERO,
    private val baseClock: Clock = Clock.System,
) : GameTime,
    GlobalTimeAdjuster {
    private val globalOffsetMillis = AtomicLong(initialGlobalOffset.inWholeMilliseconds)

    override fun now(): Instant {
        return Instant.fromEpochMilliseconds(baseClock.now().toEpochMilliseconds() + globalOffsetMillis.get())
    }

    override fun globalOffset(): Duration = globalOffsetMillis.get().milliseconds

    override fun actorOffset(): Duration = Duration.ZERO

    override fun totalOffset(): Duration = globalOffset()

    override fun setGlobalOffset(offset: Duration) {
        globalOffsetMillis.set(offset.inWholeMilliseconds)
    }

    override fun addGlobalOffset(delta: Duration) {
        globalOffsetMillis.addAndGet(delta.inWholeMilliseconds)
    }

    override fun resetGlobalOffset() {
        globalOffsetMillis.set(0)
    }

    fun actorTime(): ActorGameTime = ActorGameTime(this)
}
