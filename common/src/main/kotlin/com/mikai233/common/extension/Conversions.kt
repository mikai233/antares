@file:Suppress("unused")

package com.mikai233.common.extension

/**
 * 255 -> 00 FF
 */
fun Short.toByteArray(): ByteArray = with(toInt()) {
    byteArrayOf(
        (shr(8) and 0xFF).toByte(), (shr(0) and 0xFF).toByte(),
    )
}

/**
 * 255 -> FF 00
 */
fun Short.toByteArrayLE(): ByteArray = toByteArray().reversedArray()

/**
 * 255 -> 00 00 00 FF
 */
fun Int.toByteArray(): ByteArray = byteArrayOf(
    ushr(24).toByte(), ushr(16).toByte(), ushr(8).toByte(), ushr(0).toByte(),
)

/**
 * 255 -> FF 00 00 00
 */
fun Int.toByteArrayLE(): ByteArray = toByteArray().reversedArray()

/**
 * 255 -> 00 00 00 00 00 00 00 FF
 */
fun Long.toByteArray(): ByteArray = byteArrayOf(
    (ushr(56) and 0xFF).toByte(),
    (ushr(48) and 0xFF).toByte(),
    (ushr(40) and 0xFF).toByte(),
    (ushr(32) and 0xFF).toByte(),
    (ushr(24) and 0xFF).toByte(),
    (ushr(16) and 0xFF).toByte(),
    (ushr(8) and 0xFF).toByte(),
    (ushr(0) and 0xFF).toByte(),
)

/**
 * 255 -> FF 00 00 00 00 00 00 00
 */
fun Long.toByteArrayLE(): ByteArray = toByteArray().reversedArray()

fun ByteArray.toShort(offset: Int = 0): Short = (get(offset).toInt().shl(8) or get(offset + 1).toInt()).toShort()

fun ByteArray.toShortLE(offset: Int = 0): Short = (get(offset).toInt() or get(offset + 1).toInt().shl(8)).toShort()

fun ByteArray.toInt(offset: Int = 0): Int {
    return (get(offset).toUByte().toInt().shl(24) or
        get(offset + 1).toUByte().toInt().shl(16) or
        get(offset + 2).toUByte().toInt().shl(8) or
        get(offset + 3).toUByte().toInt())
}


fun ByteArray.toIntLE(offset: Int = 0): Int {
    return (get(offset).toUByte().toInt() or
        get(offset + 1).toUByte().toInt().shl(8) or
        get(offset + 2).toUByte().toInt().shl(16) or
        get(offset + 3).toUByte().toInt().shl(24))
}


fun ByteArray.toLong(offset: Int = 0): Long {
    return (get(offset).toUByte().toLong().shl(56) or
        get(offset + 1).toUByte().toLong().shl(48) or
        get(offset + 2).toUByte().toLong().shl(40) or
        get(offset + 3).toUByte().toLong().shl(32) or
        get(offset + 4).toUByte().toLong().shl(24) or
        get(offset + 5).toUByte().toLong().shl(16) or
        get(offset + 6).toUByte().toLong().shl(8) or
        get(offset + 7).toUByte().toLong())
}


fun ByteArray.toLongLE(offset: Int = 0): Long {
    return (get(offset).toUByte().toLong() or
        get(offset + 1).toUByte().toLong().shl(8) or
        get(offset + 2).toUByte().toLong().shl(16) or
        get(offset + 3).toUByte().toLong().shl(24) or
        get(offset + 4).toUByte().toLong().shl(32) or
        get(offset + 5).toUByte().toLong().shl(48) or
        get(offset + 6).toUByte().toLong().shl(56) or
        get(offset + 7).toUByte().toLong())
}


fun ByteArray.checkOffsetAndLength(offset: Int, length: Int) {
    require(offset >= 0) { "offset shouldn't be negative: $offset" }
    require(length >= 0) { "length shouldn't be negative: $length" }
    require(offset + length <= this.size) { "offset ($offset) + length ($length) > array.size (${this.size})" }
}

fun ByteArray.toUHexString(
    separator: String = " ",
    offset: Int = 0,
    length: Int = this.size - offset,
): String {
    checkOffsetAndLength(offset, length)
    if (length == 0) {
        return ""
    }
    val lastIndex = offset + length
    return buildString(length * 2) {
        this@toUHexString.forEachIndexed { index, byte ->
            if (index in offset..<lastIndex) {
                var ret = byte.toUByte().toString(16).uppercase()
                if (ret.length == 1) ret = "0$ret"
                append(ret)
                if (index < lastIndex - 1) append(separator)
            }
        }
    }
}
