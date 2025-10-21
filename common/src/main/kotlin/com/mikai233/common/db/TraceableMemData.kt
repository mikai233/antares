package com.mikai233.common.db

import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.serde.KryoPool
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class TraceableMemData<K, E>(
    entityClass: KClass<E>,
    kryoPool: KryoPool,
    coroutine: TrackingCoroutineScope,
    mongoTemplate: () -> MongoTemplate,
) : MemData<E> where K : Any, E : Entity {
    private val tracer = Tracer<K, E>(entityClass, kryoPool, coroutine, 2.minutes, 1.seconds, mongoTemplate)

    abstract fun entities(): Map<K, E>

    /**
     * Trace all entities
     */
    fun traceEntities() {
        tracer.trace(entities())
    }

    /**
     * Mark all entities as cleanup
     */
    fun markCleanup() {
        tracer.cleanupAll(entities())
    }

    /**
     * Flush all entities
     * @return true if all entities are flushed
     */
    fun flush(): Boolean {
        return tracer.flush(entities())
    }
}
