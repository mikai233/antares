package com.mikai233.common.db.tracked

interface PersistentValue {
    fun toPersistentValue(): Any?
}

fun persistentValueOf(value: Any?): Any? {
    return when (value) {
        is PersistentValue -> value.toPersistentValue()
        is Enum<*> -> value.name
        is IntArray -> value.toList()
        is LongArray -> value.toList()
        is BooleanArray -> value.toList()
        is DoubleArray -> value.toList()
        is FloatArray -> value.toList()
        is Map<*, *> -> value.entries.associateTo(linkedMapOf()) { (childKey, childValue) ->
            DbPath.encodePathPart(childKey) to persistentValueOf(childValue)
        }

        is List<*> -> value.map(::persistentValueOf)
        is Set<*> -> value.map(::persistentValueOf)
        is Collection<*> -> value.map(::persistentValueOf)
        else -> value
    }
}
