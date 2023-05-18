package com.mikai233.gate.codec

import com.mikai233.common.crypto.TEA
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

class CryptoCodec : ByteToMessageCodec<ByteArray>() {
//    private val cryptoKey = AttributeKey.valueOf<ByteArray>(TEA_KEY)

    override fun encode(ctx: ChannelHandlerContext, msg: ByteArray, out: ByteBuf) {
//        val key = ctx.channel().attr(cryptoKey).get()
//        val data = if (key != null) encrypt(key, msg) else msg
//        val packageLength = NETTY_PACKAGE_HEADER_LEN + data.size
//        with(out) {
//            writeInt(packageLength)
//            writeBytes(data)
//        }
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
//        val packageLenBytes = msg.readableBytes()
//        if (packageLenBytes < NETTY_PACKAGE_HEADER_LEN) {
//            return
//        }
//        msg.markReaderIndex()
//        val packageLength = msg.readInt()
//        val bodyLength = packageLength - NETTY_PACKAGE_HEADER_LEN
//        if (msg.readableBytes() < bodyLength) {
//            msg.resetReaderIndex()
//            return
//        }
//        val bytes = if (msg.hasArray()) {
//            val array = msg.array()
//            val offset = msg.arrayOffset() + msg.readerIndex()
//            array.sliceArray(offset until array.size)
//        } else {
//            val bytes = ByteArray(packageLength - NETTY_PACKAGE_HEADER_LEN)
//            msg.readBytes(bytes)
//            bytes
//        }
//        val key = ctx.channel().attr(cryptoKey).get()
//        val data = if (key != null) decrypt(key, bytes) else bytes
//        val originLen = data.toInt()
//        out.add(originLen to data.sliceArray(LZ4_HEADER_LEN until data.size))
    }

    private fun encrypt(key: ByteArray, bytes: ByteArray): ByteArray {
        return TEA.encrypt(bytes, key)
    }

    private fun decrypt(key: ByteArray, bytes: ByteArray): ByteArray {
        return TEA.decrypt(bytes, key)
    }
}