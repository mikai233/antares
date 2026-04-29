package com.mikai233.common.db.tracked

sealed class ChangeOp(open val path: DbPath) {
    data class Set(override val path: DbPath, val value: Any?) : ChangeOp(path)
    data class Unset(override val path: DbPath) : ChangeOp(path)
    data class Inc(override val path: DbPath, val delta: Number) : ChangeOp(path)
}

interface ChangeQueue {
    fun enqueue(op: ChangeOp)
}

