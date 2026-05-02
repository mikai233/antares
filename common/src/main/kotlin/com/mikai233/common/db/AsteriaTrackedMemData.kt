package com.mikai233.common.db

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.mikai233.asteria.persistence.AutoFlushMemData
import io.github.mikai233.asteria.persistence.Entity
import io.github.mikai233.asteria.persistence.mongodb.MongoTrackContext
import io.github.mikai233.asteria.persistence.mongodb.MongoTrackedDocument
import io.github.mikai233.asteria.persistence.mongodb.MongoTrackedDocumentRuntime

abstract class AsteriaTrackedMemData<E, T>(
    private val collectionName: String,
    private val mongoDatabase: () -> MongoDatabase,
    private val wrapper: (MongoTrackContext, E) -> T,
) : AutoFlushMemData where E : Entity<*>, T : MongoTrackedDocument<*, E> {
    private val runtimes = linkedMapOf<Any, MongoTrackedDocumentRuntime>()

    protected fun attachLoaded(entity: E): T {
        return createTrackedEntity(entity, enqueueCreated = false)
    }

    protected fun createTracked(entity: E): T {
        return createTrackedEntity(entity, enqueueCreated = true)
    }

    protected fun removeTracked(entityId: Any) {
        runtimes.remove(entityId)
    }

    override suspend fun tick() {
        flush()
    }

    override suspend fun flush(): Boolean {
        return runtimes.values.toList().all { it.flushSafely() }
    }

    private fun createTrackedEntity(entity: E, enqueueCreated: Boolean): T {
        val entityId = entity.id
        require(entityId !in runtimes) { "tracked Mongo document $collectionName:$entityId is already loaded" }
        val runtime = MongoTrackedDocumentRuntime(collectionName, entityId, mongoDatabase())
        val tracked = wrapper(runtime.context(), entity)
        if (enqueueCreated) {
            runtime.enqueueCreated(tracked)
        }
        runtimes[entityId] = runtime
        return tracked
    }
}
