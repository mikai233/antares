package com.mikai233.common.db.tracked

data class WriteKey(
    val slot: String,
    val bucket: Int,
    val entityId: String,
)

data class DbPath(
    val slot: String,
    val bucket: Int,
    val entityId: String,
    val fieldPath: String,
) {
    val writeKey: WriteKey
        get() = WriteKey(slot, bucket, entityId)

    fun child(part: Any?): DbPath {
        val childPath = encodePathPart(part)
        return copy(fieldPath = if (fieldPath.isEmpty()) childPath else "$fieldPath.$childPath")
    }

    fun dataDepth(): Int {
        if (fieldPath.isEmpty()) {
            return 0
        }
        val parts = fieldPath.split(".")
        return if (parts.firstOrNull() == "data") parts.size - 1 else parts.size
    }

    companion object {
        fun encodePathPart(part: Any?): String {
            val rawPart = when (part) {
                is Enum<*> -> part.name
                else -> part.toString()
            }
            return rawPart
                .replace("%", "%25")
                .replace(".", "%2E")
                .replace("$", "%24")
        }
    }
}
