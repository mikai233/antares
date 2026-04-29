package com.mikai233.common.db.tracked

import java.util.*

@Suppress("UNCHECKED_CAST")
private const val DEFAULT_WRITE_BOUNDARY_DEPTH = 2

data class DirtyTarget(
    val path: DbPath,
    val value: Any?,
)

interface DirtyTargetAware {
    fun bindDirtyTarget(dirtyTarget: DirtyTarget?)
}

internal fun ChangeQueue.enqueueSet(path: DbPath, value: Any?, dirtyTarget: DirtyTarget?) {
    if (dirtyTarget == null) {
        enqueue(ChangeOp.Set(path, value))
    } else {
        enqueue(ChangeOp.Set(dirtyTarget.path, dirtyTarget.value))
    }
}

internal fun ChangeQueue.enqueueUnset(path: DbPath, dirtyTarget: DirtyTarget?) {
    if (dirtyTarget == null) {
        enqueue(ChangeOp.Unset(path))
    } else {
        enqueue(ChangeOp.Set(dirtyTarget.path, dirtyTarget.value))
    }
}

@Suppress("UNCHECKED_CAST")
internal fun trackMutableValue(
    path: DbPath,
    value: Any?,
    queue: ChangeQueue,
    dirtyTarget: DirtyTarget? = null,
): Any? {
    /*
     * Future KSP optimization:
     * Immutable values should pass through this function unchanged. For val fields whose type is a
     * primitive/String/Enum or an immutable object graph made only of val immutable fields, KSP should
     * not generate wrappers at all. Wrappers are only useful for mutable objects or mutable containers
     * that can change in place.
     */
    val effectiveDirtyTarget = dirtyTarget ?: writeBoundaryFor(path, value)
    if (value is DirtyTargetAware) {
        value.bindDirtyTarget(effectiveDirtyTarget)
        return value
    }
    return when (value) {
        is PersistentValue -> value
        is MutableMap<*, *> -> TrackedMutableMap(
            path,
            value as MutableMap<Any?, Any?>,
            queue,
            dirtyTarget = effectiveDirtyTarget,
        )

        is MutableList<*> -> TrackedMutableList(
            path,
            value as MutableList<Any?>,
            queue,
            dirtyTarget = effectiveDirtyTarget,
        )

        is MutableSet<*> -> TrackedMutableSet(
            path,
            value as MutableSet<Any?>,
            queue,
            dirtyTarget = effectiveDirtyTarget,
        )

        is Deque<*> -> TrackedMutableDeque(
            path,
            value as Deque<Any?>,
            queue,
            dirtyTarget = effectiveDirtyTarget,
        )

        else -> value
    }
}

private fun writeBoundaryFor(path: DbPath, value: Any?): DirtyTarget? {
    if (path.dataDepth() < DEFAULT_WRITE_BOUNDARY_DEPTH || !canHaveNestedMutation(value)) {
        return null
    }
    return DirtyTarget(path, value)
}

private fun canHaveNestedMutation(value: Any?): Boolean {
    return value is DirtyTargetAware ||
            value is PersistentValue ||
            value is MutableMap<*, *> ||
            value is MutableList<*> ||
            value is MutableSet<*> ||
            value is Deque<*>
}
