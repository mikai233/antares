package com.mikai233.common.db.tracked

import kotlin.reflect.KProperty

class TrackedIntArrayDelegate(
    trackedArray: TrackedIntArray,
) {
    private val value = trackedArray

    operator fun getValue(thisRef: Any?, property: KProperty<*>): TrackedIntArray {
        return value
    }
}

fun trackedIntArray(
    path: DbPath,
    initialValue: IntArray,
    queue: ChangeQueue,
): TrackedIntArrayDelegate {
    return TrackedIntArrayDelegate(TrackedIntArray(path, initialValue, queue))
}

class TrackedIntArray(
    private val path: DbPath,
    initialValue: IntArray,
    private val queue: ChangeQueue,
) : PersistentValue {
    private val backing: IntArray = initialValue

    val size: Int
        get() = backing.size

    operator fun get(index: Int): Int {
        return backing[index]
    }

    operator fun set(index: Int, value: Int) {
        if (backing[index] == value) {
            return
        }
        backing[index] = value
        queue.enqueue(ChangeOp.Set(path.child(index), value))
    }

    fun toIntArray(): IntArray {
        return backing.copyOf()
    }

    override fun toPersistentValue(): Any? {
        return backing.toList()
    }
}

class TrackedLongArrayDelegate(
    trackedArray: TrackedLongArray,
) {
    private val value = trackedArray

    operator fun getValue(thisRef: Any?, property: KProperty<*>): TrackedLongArray {
        return value
    }
}

fun trackedLongArray(
    path: DbPath,
    initialValue: LongArray,
    queue: ChangeQueue,
): TrackedLongArrayDelegate {
    return TrackedLongArrayDelegate(TrackedLongArray(path, initialValue, queue))
}

class TrackedLongArray(
    private val path: DbPath,
    initialValue: LongArray,
    private val queue: ChangeQueue,
) : PersistentValue {
    private val backing: LongArray = initialValue

    val size: Int
        get() = backing.size

    operator fun get(index: Int): Long {
        return backing[index]
    }

    operator fun set(index: Int, value: Long) {
        if (backing[index] == value) {
            return
        }
        backing[index] = value
        queue.enqueue(ChangeOp.Set(path.child(index), value))
    }

    fun toLongArray(): LongArray {
        return backing.copyOf()
    }

    override fun toPersistentValue(): Any? {
        return backing.toList()
    }
}

class TrackedBooleanArrayDelegate(
    trackedArray: TrackedBooleanArray,
) {
    private val value = trackedArray

    operator fun getValue(thisRef: Any?, property: KProperty<*>): TrackedBooleanArray {
        return value
    }
}

fun trackedBooleanArray(
    path: DbPath,
    initialValue: BooleanArray,
    queue: ChangeQueue,
): TrackedBooleanArrayDelegate {
    return TrackedBooleanArrayDelegate(TrackedBooleanArray(path, initialValue, queue))
}

class TrackedBooleanArray(
    private val path: DbPath,
    initialValue: BooleanArray,
    private val queue: ChangeQueue,
) : PersistentValue {
    private val backing: BooleanArray = initialValue

    val size: Int
        get() = backing.size

    operator fun get(index: Int): Boolean {
        return backing[index]
    }

    operator fun set(index: Int, value: Boolean) {
        if (backing[index] == value) {
            return
        }
        backing[index] = value
        queue.enqueue(ChangeOp.Set(path.child(index), value))
    }

    fun toBooleanArray(): BooleanArray {
        return backing.copyOf()
    }

    override fun toPersistentValue(): Any? {
        return backing.toList()
    }
}

class TrackedDoubleArrayDelegate(
    trackedArray: TrackedDoubleArray,
) {
    private val value = trackedArray

    operator fun getValue(thisRef: Any?, property: KProperty<*>): TrackedDoubleArray {
        return value
    }
}

fun trackedDoubleArray(
    path: DbPath,
    initialValue: DoubleArray,
    queue: ChangeQueue,
): TrackedDoubleArrayDelegate {
    return TrackedDoubleArrayDelegate(TrackedDoubleArray(path, initialValue, queue))
}

class TrackedDoubleArray(
    private val path: DbPath,
    initialValue: DoubleArray,
    private val queue: ChangeQueue,
) : PersistentValue {
    private val backing: DoubleArray = initialValue

    val size: Int
        get() = backing.size

    operator fun get(index: Int): Double {
        return backing[index]
    }

    operator fun set(index: Int, value: Double) {
        if (backing[index] == value) {
            return
        }
        backing[index] = value
        queue.enqueue(ChangeOp.Set(path.child(index), value))
    }

    fun toDoubleArray(): DoubleArray {
        return backing.copyOf()
    }

    override fun toPersistentValue(): Any? {
        return backing.toList()
    }
}

class TrackedFloatArrayDelegate(
    trackedArray: TrackedFloatArray,
) {
    private val value = trackedArray

    operator fun getValue(thisRef: Any?, property: KProperty<*>): TrackedFloatArray {
        return value
    }
}

fun trackedFloatArray(
    path: DbPath,
    initialValue: FloatArray,
    queue: ChangeQueue,
): TrackedFloatArrayDelegate {
    return TrackedFloatArrayDelegate(TrackedFloatArray(path, initialValue, queue))
}

class TrackedFloatArray(
    private val path: DbPath,
    initialValue: FloatArray,
    private val queue: ChangeQueue,
) : PersistentValue {
    private val backing: FloatArray = initialValue

    val size: Int
        get() = backing.size

    operator fun get(index: Int): Float {
        return backing[index]
    }

    operator fun set(index: Int, value: Float) {
        if (backing[index] == value) {
            return
        }
        backing[index] = value
        queue.enqueue(ChangeOp.Set(path.child(index), value))
    }

    fun toFloatArray(): FloatArray {
        return backing.copyOf()
    }

    override fun toPersistentValue(): Any? {
        return backing.toList()
    }
}
