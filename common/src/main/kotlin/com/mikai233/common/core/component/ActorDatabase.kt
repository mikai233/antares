package com.mikai233.common.core.component

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.db.DataTracer
import com.mikai233.common.inject.XKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActorDatabase(private val koin: XKoin, coroutine: ActorCoroutine) : KoinComponent by koin {
    val mongoHolder: MongoHolder by inject()
    val tracer = DataTracer(mongoHolder::getGameTemplate, coroutine)

    fun stopAndFlushAll(): Boolean {
        if (tracer.stopped.not()) {
            tracer.stopTrace()
        }
        return tracer.isAllPendingDataFlushedToDb()
    }
}
