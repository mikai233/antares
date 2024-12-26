package com.mikai233.common.db

import com.google.common.hash.HashCode

/**
 * @param hashSameCount: the count of the same hash code
 * @param hashCode: the hash code of the object
 * @param fullHashCode: the full hash code of the object
 * @param status: mark the object is dirty or not
 * @param value: if dirty, save the object
 */
data class Record(
    var hashSameCount: Int,
    var hashCode: Int,
    var fullHashCode: HashCode,
    var status: Status,
    var value: Any?,
) {
    companion object {
        fun default(): Record {
            return Record(0, 0, HashCode.fromInt(0), Status.Clean, null)
        }
    }
}
