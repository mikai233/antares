package com.mikai233.common.db.tracked

data class TrackContext(
    val slot: String,
    val bucket: Int,
    val entityId: Any?,
    val queue: ChangeQueue,
    val fieldRoot: String = "",
) {
    fun path(fieldName: String): DbPath {
        val encodedFieldName = DbPath.encodePathPart(fieldName)
        val fieldPath = if (fieldRoot.isBlank()) {
            encodedFieldName
        } else {
            "${DbPath.encodePathPart(fieldRoot)}.$encodedFieldName"
        }
        return DbPath(slot, bucket, entityId, fieldPath)
    }
}
