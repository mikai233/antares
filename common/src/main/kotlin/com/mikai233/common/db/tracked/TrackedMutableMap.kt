package com.mikai233.common.db.tracked

import kotlin.reflect.KProperty

class TrackedMapDelegate<K, V>(
    trackedMap: TrackedMutableMap<K, V>,
) {
    private val value: MutableMap<K, V> = trackedMap

    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableMap<K, V> {
        return value
    }
}

fun <K, V> trackedMap(
    path: DbPath,
    initialValue: MutableMap<K, V>,
    queue: ChangeQueue,
    persistentValue: (V) -> Any? = ::persistentValueOf,
    trackedValue: ((K, V) -> V)? = null,
    dirtyTarget: DirtyTarget? = null,
    dirtyTargetProvider: (() -> DirtyTarget?)? = null,
): TrackedMapDelegate<K, V> {
    return TrackedMapDelegate(
        TrackedMutableMap(path, initialValue, queue, persistentValue, trackedValue, dirtyTarget, dirtyTargetProvider),
    )
}

class TrackedMutableMap<K, V>(
    private val path: DbPath,
    initialValue: MutableMap<K, V>,
    private val queue: ChangeQueue,
    private val persistentValue: (V) -> Any? = ::persistentValueOf,
    private val trackedValue: ((K, V) -> V)? = null,
    private val dirtyTarget: DirtyTarget? = null,
    private val dirtyTargetProvider: (() -> DirtyTarget?)? = null,
) : AbstractMutableMap<K, V>(), PersistentValue {
    private val backing: MutableMap<K, V> = initialValue

    init {
        backing.entries.toList().forEach { (key, value) ->
            backing[key] = trackValue(key, value)
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = TrackedEntrySet()

    override val size: Int
        get() = backing.size

    override fun containsKey(key: K): Boolean {
        return backing.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        return backing.containsValue(value)
    }

    override fun get(key: K): V? {
        return backing[key]
    }

    override fun put(key: K, value: V): V? {
        val valueToStore = trackValue(key, value)
        val existed = backing.containsKey(key)
        val previous = backing.put(key, valueToStore)
        if (!existed || persistentValueOf(previous) != persistentValueOf(valueToStore)) {
            queue.enqueueSet(path.child(key), valueToStore, currentDirtyTarget())
        }
        return previous
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun remove(key: K): V? {
        val existed = backing.containsKey(key)
        val previous = backing.remove(key)
        if (existed) {
            queue.enqueueUnset(path.child(key), currentDirtyTarget())
        }
        return previous
    }

    override fun clear() {
        if (backing.isEmpty()) {
            return
        }
        backing.clear()
        queue.enqueueSet(path, emptyMap<K, V>(), currentDirtyTarget())
    }

    override fun toPersistentValue(): Any? {
        return backing.entries.associateTo(linkedMapOf()) { (key, value) ->
            DbPath.encodePathPart(key) to persistentValue(value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackValue(key: K, value: V): V {
        return trackedValue?.invoke(key, value)
            ?: trackMutableValue(path.child(key), value, queue, currentDirtyTarget()) as V
    }

    private fun currentDirtyTarget(): DirtyTarget? {
        return dirtyTargetProvider?.invoke() ?: dirtyTarget
    }

    private inner class TrackedEntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int
            get() = backing.entries.size

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            throw UnsupportedOperationException("MutableMap entries do not support add")
        }

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            return backing.entries.any { it.key == element.key && it.value == element.value }
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return TrackedEntryIterator(backing.entries.iterator())
        }

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            if (!contains(element)) {
                return false
            }
            this@TrackedMutableMap.remove(element.key)
            return true
        }
    }

    private inner class TrackedEntryIterator(
        private val iterator: MutableIterator<MutableMap.MutableEntry<K, V>>,
    ) : MutableIterator<MutableMap.MutableEntry<K, V>> {
        private var current: MutableMap.MutableEntry<K, V>? = null

        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): MutableMap.MutableEntry<K, V> {
            val next = iterator.next()
            current = next
            return TrackedEntry(next)
        }

        override fun remove() {
            val entry = requireNotNull(current) { "next() must be called before remove()" }
            iterator.remove()
            queue.enqueueUnset(path.child(entry.key), currentDirtyTarget())
            current = null
        }
    }

    private inner class TrackedEntry(
        private val entry: MutableMap.MutableEntry<K, V>,
    ) : MutableMap.MutableEntry<K, V> {
        override val key: K
            get() = entry.key

        override val value: V
            get() = entry.value

        override fun setValue(newValue: V): V {
            val valueToStore = trackValue(key, newValue)
            val previous = entry.setValue(valueToStore)
            if (persistentValueOf(previous) != persistentValueOf(valueToStore)) {
                queue.enqueueSet(path.child(key), valueToStore, currentDirtyTarget())
            }
            return previous
        }
    }
}
