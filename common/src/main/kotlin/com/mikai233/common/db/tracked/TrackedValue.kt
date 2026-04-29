package com.mikai233.common.db.tracked

import kotlin.reflect.KProperty

class TrackedValue<T>(
    private val path: DbPath,
    initialValue: T,
    private val queue: ChangeQueue,
    private val dirtyTarget: () -> DirtyTarget? = { null },
) {
    private var value = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        if (value == newValue) {
            return
        }
        value = newValue
        queue.enqueueSet(path, newValue, dirtyTarget())
    }
}

fun <T> trackedValue(
    path: DbPath,
    initialValue: T,
    queue: ChangeQueue,
    dirtyTarget: DirtyTarget? = null,
): TrackedValue<T> {
    return TrackedValue(path, initialValue, queue) { dirtyTarget }
}

fun <T> trackedValue(
    path: DbPath,
    initialValue: T,
    queue: ChangeQueue,
    dirtyTarget: () -> DirtyTarget?,
): TrackedValue<T> {
    return TrackedValue(path, initialValue, queue, dirtyTarget)
}
