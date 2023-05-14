package com.mikai233.codec

data class Packet(val index: Int, val compressedLen: Long, val body: ByteArray) {
    companion object {
        const val HEADER_LEN = UInt.SIZE_BYTES + UShort.SIZE_BYTES + UInt.SIZE_BYTES
    }

    fun packetLen(): Int {
        return HEADER_LEN + body.size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (index != other.index) return false
        if (compressedLen != other.compressedLen) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + compressedLen.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}
