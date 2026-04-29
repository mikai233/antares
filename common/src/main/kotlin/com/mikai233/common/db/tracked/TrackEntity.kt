package com.mikai233.common.db.tracked

import com.mikai233.common.db.Entity

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TrackEntity

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class TrackIgnore

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class StableId

interface TrackedEntity<E> : Entity, PersistentValue where E : Entity {
    val trackId: Any?

    fun toEntity(): E
}
