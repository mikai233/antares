package com.mikai233.common.time

import kotlinx.datetime.TimeZone
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class ActorGameTime(
    private val global: GameTimeSource,
) : GameTime,
    ActorTimeAdjuster {
    private val actorOffsetMillis = AtomicLong(0)

    override val timeZone: TimeZone
        get() = global.timeZone

    override fun now(): Instant {
        return Instant.fromEpochMilliseconds(global.nowMillis() + actorOffsetMillis.get())
    }

    override fun globalOffset(): Duration = global.globalOffset()

    override fun actorOffset(): Duration = actorOffsetMillis.get().milliseconds

    override fun totalOffset(): Duration = globalOffset() + actorOffset()

    override fun setActorOffset(offset: Duration) {
        actorOffsetMillis.set(offset.inWholeMilliseconds)
    }

    override fun addActorOffset(delta: Duration) {
        actorOffsetMillis.addAndGet(delta.inWholeMilliseconds)
    }

    override fun resetActorOffset() {
        actorOffsetMillis.set(0)
    }
}
