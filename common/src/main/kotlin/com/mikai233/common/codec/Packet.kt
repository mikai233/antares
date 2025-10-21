package com.mikai233.common.codec

/**
 * @param protoId proto number max value:536,870,911
 * @param originLen
 */
data class Packet(val protoId: Int, val originLen: Int, var body: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (protoId != other.protoId) return false
        if (originLen != other.originLen) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protoId
        result = 31 * result + originLen
        result = 31 * result + body.contentHashCode()
        return result
    }
}
