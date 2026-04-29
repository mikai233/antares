package com.mikai233.common.db.tracked

import java.util.*
import kotlin.reflect.KProperty

class TrackedDequeDelegate<E>(
    trackedDeque: TrackedMutableDeque<E>,
) {
    private val value: Deque<E> = trackedDeque

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Deque<E> {
        return value
    }
}

fun <E> trackedDeque(
    path: DbPath,
    initialValue: Deque<E>,
    queue: ChangeQueue,
    persistentValue: (E) -> Any? = ::persistentValueOf,
    trackedValue: ((Int, E) -> E)? = null,
    dirtyTarget: DirtyTarget? = null,
    dirtyTargetProvider: (() -> DirtyTarget?)? = null,
): TrackedDequeDelegate<E> {
    return TrackedDequeDelegate(
        TrackedMutableDeque(path, initialValue, queue, persistentValue, trackedValue, dirtyTarget, dirtyTargetProvider),
    )
}

class TrackedMutableDeque<E>(
    private val path: DbPath,
    initialValue: Deque<E>,
    private val queue: ChangeQueue,
    private val persistentValue: (E) -> Any? = ::persistentValueOf,
    private val trackedValue: ((Int, E) -> E)? = null,
    private val dirtyTarget: DirtyTarget? = null,
    private val dirtyTargetProvider: (() -> DirtyTarget?)? = null,
) : AbstractQueue<E>(), Deque<E>, PersistentValue {
    private val backing: Deque<E> = initialValue

    init {
        val values = backing.toList()
        backing.clear()
        values.forEachIndexed { index, value -> backing.addLast(trackValue(index, value)) }
    }

    override val size: Int
        get() = backing.size

    override fun addFirst(e: E) {
        backing.addFirst(trackValue(0, e))
        markWhole()
    }

    override fun addLast(e: E) {
        backing.addLast(trackValue(backing.size, e))
        markWhole()
    }

    override fun offerFirst(e: E): Boolean {
        val changed = backing.offerFirst(trackValue(0, e))
        if (changed) {
            markWhole()
        }
        return changed
    }

    override fun offerLast(e: E): Boolean {
        val changed = backing.offerLast(trackValue(backing.size, e))
        if (changed) {
            markWhole()
        }
        return changed
    }

    override fun removeFirst(): E {
        val value = backing.removeFirst()
        markWhole()
        return value
    }

    override fun removeLast(): E {
        val value = backing.removeLast()
        markWhole()
        return value
    }

    override fun pollFirst(): E? {
        val value = backing.pollFirst()
        if (value != null) {
            markWhole()
        }
        return value
    }

    override fun pollLast(): E? {
        val value = backing.pollLast()
        if (value != null) {
            markWhole()
        }
        return value
    }

    override fun getFirst(): E {
        return backing.first
    }

    override fun getLast(): E {
        return backing.last
    }

    override fun peekFirst(): E? {
        return backing.peekFirst()
    }

    override fun peekLast(): E? {
        return backing.peekLast()
    }

    override fun removeFirstOccurrence(o: Any?): Boolean {
        val changed = backing.removeFirstOccurrence(o)
        if (changed) {
            markWhole()
        }
        return changed
    }

    override fun removeLastOccurrence(o: Any?): Boolean {
        val changed = backing.removeLastOccurrence(o)
        if (changed) {
            markWhole()
        }
        return changed
    }

    override fun offer(e: E): Boolean {
        return offerLast(e)
    }

    override fun poll(): E? {
        return pollFirst()
    }

    override fun peek(): E? {
        return peekFirst()
    }

    override fun push(e: E) {
        addFirst(e)
    }

    override fun pop(): E {
        return removeFirst()
    }

    override fun iterator(): MutableIterator<E> {
        return TrackedIterator(backing.iterator())
    }

    override fun descendingIterator(): MutableIterator<E> {
        return TrackedIterator(backing.descendingIterator())
    }

    override fun clear() {
        if (backing.isEmpty()) {
            return
        }
        backing.clear()
        markWhole()
    }

    override fun toPersistentValue(): Any {
        return backing.map(persistentValue)
    }

    private fun markWhole() {
        queue.enqueueSet(path, this, currentDirtyTarget())
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackValue(index: Int, value: E): E {
        return trackedValue?.invoke(index, value)
            ?: trackMutableValue(path.child(index), value, queue, currentDirtyTarget()) as E
    }

    private fun currentDirtyTarget(): DirtyTarget? {
        return dirtyTargetProvider?.invoke() ?: dirtyTarget
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

    companion object {
        fun <E> linked(path: DbPath, values: Iterable<E>, queue: ChangeQueue): TrackedMutableDeque<E> {
            return TrackedMutableDeque(path, LinkedList(values.toList()), queue)
        }
    }
}
