package com.mikai233.common.time

import kotlin.time.Duration

interface GlobalTimeAdjuster {
    fun setGlobalOffset(offset: Duration)

    fun addGlobalOffset(delta: Duration)

    fun resetGlobalOffset()
}

interface ActorTimeAdjuster {
    fun setActorOffset(offset: Duration)

    fun addActorOffset(delta: Duration)

    fun resetActorOffset()
}
