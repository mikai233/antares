package com.mikai233.common.db.tracked

import kotlin.reflect.KProperty

class TrackedListDelegate<E>(
    trackedList: TrackedMutableList<E>,
) {
    private val value: MutableList<E> = trackedList

    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableList<E> {
        return value
    }
}

fun <E> trackedList(
    path: DbPath,
    initialValue: MutableList<E>,
    queue: ChangeQueue,
    persistentValue: (E) -> Any? = ::persistentValueOf,
    trackedValue: ((Int, E) -> E)? = null,
    dirtyTarget: DirtyTarget? = null,
    dirtyTargetProvider: (() -> DirtyTarget?)? = null,
): TrackedListDelegate<E> {
    return TrackedListDelegate(
        TrackedMutableList(path, initialValue, queue, persistentValue, trackedValue, dirtyTarget, dirtyTargetProvider),
    )
}

class TrackedMutableList<E>(
    private val path: DbPath,
    initialValue: MutableList<E>,
    private val queue: ChangeQueue,
    private val persistentValue: (E) -> Any? = ::persistentValueOf,
    private val trackedValue: ((Int, E) -> E)? = null,
    private val dirtyTarget: DirtyTarget? = null,
    private val dirtyTargetProvider: (() -> DirtyTarget?)? = null,
) : AbstractMutableList<E>(), PersistentValue {
    private val backing: MutableList<E> = initialValue

    init {
        val iterator = backing.listIterator()
        while (iterator.hasNext()) {
            val index = iterator.nextIndex()
            val value = iterator.next()
            iterator.set(trackValue(index, value))
        }
    }

    override val size: Int
        get() = backing.size

    override fun get(index: Int): E {
        return backing[index]
    }

    override fun set(index: Int, element: E): E {
        val valueToStore = trackValue(index, element)
        val previous = backing.set(index, valueToStore)
        if (persistentValueOf(previous) != persistentValueOf(valueToStore)) {
            queue.enqueueSet(path.child(index), valueToStore, currentDirtyTarget())
        }
        return previous
    }

    override fun add(index: Int, element: E) {
        backing.add(index, trackValue(index, element))
        queue.enqueueSet(path, this, currentDirtyTarget())
    }

    override fun removeAt(index: Int): E {
        val previous = backing.removeAt(index)
        queue.enqueueSet(path, this, currentDirtyTarget())
        return previous
    }

    override fun clear() {
        if (backing.isEmpty()) {
            return
        }
        backing.clear()
        queue.enqueueSet(path, emptyList<E>(), currentDirtyTarget())
    }

    override fun toPersistentValue(): Any? {
        return backing.map(persistentValue)
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackValue(index: Int, value: E): E {
        return trackedValue?.invoke(index, value)
            ?: trackMutableValue(path.child(index), value, queue, currentDirtyTarget()) as E
    }

    private fun currentDirtyTarget(): DirtyTarget? {
        return dirtyTargetProvider?.invoke() ?: dirtyTarget
    }
}
