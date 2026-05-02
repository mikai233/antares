package com.mikai233.common.db.tracked

import io.github.mikai233.asteria.persistence.Entity

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TrackEntity

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class TrackIgnore

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class StableId

interface TrackedEntity<E> : PersistentValue where E : Entity<*> {
    val trackId: Any?

    fun toEntity(): E
}
