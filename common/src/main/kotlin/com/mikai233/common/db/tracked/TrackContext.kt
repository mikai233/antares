package com.mikai233.common.db.tracked

data class TrackContext(
    val slot: String,
    val bucket: Int,
    val entityId: String,
    val queue: ChangeQueue,
) {
    fun path(fieldName: String): DbPath {
        return DbPath(slot, bucket, entityId, "data.${DbPath.encodePathPart(fieldName)}")
    }
}

