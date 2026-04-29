package com.mikai233.common.db.tracked

import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

data class PendingWrite(
    val key: WriteKey,
    val sets: Map<String, Any?>,
    val unsets: Set<String>,
    val incs: Map<String, Number>,
) {
    fun query(idField: String = "_id"): Query {
        return Query.query(Criteria.where(idField).`is`(key.entityId))
    }

    fun update(): Update {
        val update = Update()
        sets.forEach { (path, value) -> update.set(path, value) }
        unsets.forEach { path -> update.unset(path) }
        incs.forEach { (path, delta) -> update.inc(path, delta) }
        return update
    }
}

class PendingWriteQueue : ChangeQueue {
    private val patches: MutableMap<WriteKey, PendingPatch> = linkedMapOf()

    @Synchronized
    override fun enqueue(op: ChangeOp) {
        val patch = patches.getOrPut(op.path.writeKey) { PendingPatch() }
        patch.merge(op)
    }

    @Synchronized
    fun drain(): List<PendingWrite> {
        val writes = patches.map { (key, patch) -> patch.toWrite(key) }
        patches.clear()
        return writes.filterNot { it.sets.isEmpty() && it.unsets.isEmpty() && it.incs.isEmpty() }
    }

    @Synchronized
    fun requeue(writes: Iterable<PendingWrite>) {
        writes.forEach { write ->
            write.sets.forEach { (fieldPath, value) ->
                enqueue(ChangeOp.Set(write.key.path(fieldPath), value))
            }
            write.unsets.forEach { fieldPath ->
                enqueue(ChangeOp.Unset(write.key.path(fieldPath)))
            }
            write.incs.forEach { (fieldPath, delta) ->
                enqueue(ChangeOp.Inc(write.key.path(fieldPath), delta))
            }
        }
    }

    @Synchronized
    fun snapshot(): List<PendingWrite> {
        return patches.map { (key, patch) -> patch.toWrite(key) }
            .filterNot { it.sets.isEmpty() && it.unsets.isEmpty() && it.incs.isEmpty() }
    }
}

private fun WriteKey.path(fieldPath: String): DbPath {
    return DbPath(slot, bucket, entityId, fieldPath)
}

private class PendingPatch {
    private val sets: MutableMap<String, Any?> = linkedMapOf()
    private val unsets: MutableSet<String> = linkedSetOf()
    private val incs: MutableMap<String, Number> = linkedMapOf()

    fun merge(op: ChangeOp) {
        when (op) {
            is ChangeOp.Set -> set(op.path.fieldPath, op.value)
            is ChangeOp.Unset -> unset(op.path.fieldPath)
            is ChangeOp.Inc -> inc(op.path.fieldPath, op.delta)
        }
    }

    private fun set(path: String, value: Any?) {
        if (hasAncestorOperation(path)) {
            return
        }
        removeDescendantOperations(path)
        unsets.remove(path)
        incs.remove(path)
        sets[path] = value
    }

    private fun unset(path: String) {
        if (hasAncestorOperation(path)) {
            return
        }
        removeDescendantOperations(path)
        sets.remove(path)
        incs.remove(path)
        unsets.add(path)
    }

    private fun inc(path: String, delta: Number) {
        if (hasAncestorOperation(path) || path in sets || path in unsets) {
            return
        }
        incs[path] = addNumbers(incs[path], delta)
    }

    private fun hasAncestorOperation(path: String): Boolean {
        return ancestors(path).any { ancestor -> ancestor in sets || ancestor in unsets || ancestor in incs }
    }

    private fun removeDescendantOperations(path: String) {
        val prefix = "$path."
        sets.keys.removeIf { it.startsWith(prefix) }
        unsets.removeIf { it.startsWith(prefix) }
        incs.keys.removeIf { it.startsWith(prefix) }
    }

    private fun ancestors(path: String): Sequence<String> = sequence {
        var index = path.lastIndexOf('.')
        while (index > 0) {
            yield(path.substring(0, index))
            index = path.lastIndexOf('.', index - 1)
        }
    }

    private fun addNumbers(left: Number?, right: Number): Number {
        return when {
            left == null -> right
            left is Double || right is Double -> left.toDouble() + right.toDouble()
            left is Float || right is Float -> left.toFloat() + right.toFloat()
            left is Long || right is Long -> left.toLong() + right.toLong()
            else -> left.toInt() + right.toInt()
        }
    }

    fun toWrite(key: WriteKey): PendingWrite {
        return PendingWrite(
            key,
            sets.mapValues { (_, value) -> persistentValueOf(value) },
            unsets.toSet(),
            incs.toMap(),
        )
    }
}
