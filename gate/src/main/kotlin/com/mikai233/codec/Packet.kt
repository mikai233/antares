package com.mikai233.codec

data class Packet(val len: UInt, val index: UShort, val compressedLen: UInt, val data: ByteArray)
