package com.mikai233.common.db.tracked

import kotlin.reflect.KProperty

class TrackedSetDelegate<E>(
    trackedSet: TrackedMutableSet<E>,
) {
    private val value: MutableSet<E> = trackedSet

    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableSet<E> {
        return value
    }
}

fun <E> trackedSet(
    path: DbPath,
    initialValue: MutableSet<E>,
    queue: ChangeQueue,
    persistentValue: (E) -> Any? = ::persistentValueOf,
    trackedValue: ((E) -> E)? = null,
    dirtyTarget: DirtyTarget? = null,
): TrackedSetDelegate<E> {
    return TrackedSetDelegate(TrackedMutableSet(path, initialValue, queue, persistentValue, trackedValue, dirtyTarget))
}

class TrackedMutableSet<E>(
    private val path: DbPath,
    initialValue: MutableSet<E>,
    private val queue: ChangeQueue,
    private val persistentValue: (E) -> Any? = ::persistentValueOf,
    private val trackedValue: ((E) -> E)? = null,
    private val dirtyTarget: DirtyTarget? = null,
) : AbstractMutableSet<E>(), PersistentValue {
    private val backing: MutableSet<E> = initialValue

    /*
     * Important for future KSP generation:
     * Mutable objects stored in a Set must have stable identity-based equals/hashCode.
     * Do not include mutable tracked fields in equals/hashCode, otherwise HashSet/LinkedHashSet
     * may lose the element after an inner field changes. For generated tracked objects that can
     * appear as Set elements, generate equality from an immutable id/@StableId field only.
     *
     * Changes inside a Set element are intentionally lifted to a whole-set $set via DirtyTarget,
     * because a Set element has no stable Mongo path.
     */
    init {
        val values = backing.toList()
        backing.clear()
        values.forEach { value -> backing.add(trackValue(value)) }
    }

    override val size: Int
        get() = backing.size

    override fun add(element: E): Boolean {
        val changed = backing.add(trackValue(element))
        if (changed) {
            markWhole()
        }
        return changed
    }

    override fun contains(element: E): Boolean {
        return backing.contains(element)
    }

    override fun iterator(): MutableIterator<E> {
        return TrackedIterator(backing.iterator())
    }

    override fun remove(element: E): Boolean {
        val changed = backing.remove(element)
        if (changed) {
            markWhole()
        }
        return changed
    }

    override fun clear() {
        if (backing.isEmpty()) {
            return
        }
        backing.clear()
        queue.enqueueSet(path, emptySet<E>(), dirtyTarget)
    }

    override fun toPersistentValue(): Any? {
        return backing.map(persistentValue)
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackValue(value: E): E {
        return trackedValue?.invoke(value) ?: trackMutableValue(path, value, queue, DirtyTarget(path, this)) as E
    }

    private fun markWhole() {
        queue.enqueueSet(path, this, dirtyTarget)
    }

    private inner class TrackedIterator(
        private val iterator: MutableIterator<E>,
    ) : MutableIterator<E> {
        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): E {
            return iterator.next()
        }

        override fun remove() {
            iterator.remove()
            markWhole()
        }
    }
}
