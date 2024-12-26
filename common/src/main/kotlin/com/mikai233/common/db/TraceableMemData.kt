package com.mikai233.common.db

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.serde.KryoPool
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.reflect.KClass

abstract class TraceableMemData<K, E>(
    entityClass: KClass<E>,
    kryoPool: KryoPool,
    coroutine: ActorCoroutine,
    mongoTemplate: () -> MongoTemplate
) : MemData<E> where K : Any, E : Entity {
    private val tracer = Tracer<K, E>(entityClass, kryoPool, coroutine, mongoTemplate)

    abstract fun entities(): Map<K, E>

    fun traceEntities() {
        tracer.trace(entities())
    }

    fun flush() {
    }
}