package com.mikai233.gate.codec

/**
 * @param index
 * @param protoId proto number max value:536,870,911
 * @param originLen
 */
data class Packet(val index: Int, val protoId: Int, val originLen: Int, var body: ByteArray) {
    companion object {
        const val PACKET_LEN = Int.SIZE_BYTES
        const val HEADER_LEN = 4 * Int.SIZE_BYTES
    }

    fun packetLen(): Int {
        return HEADER_LEN + body.size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (index != other.index) return false
        if (protoId != other.protoId) return false
        if (originLen != other.originLen) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + protoId
        result = 31 * result + originLen.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

}
