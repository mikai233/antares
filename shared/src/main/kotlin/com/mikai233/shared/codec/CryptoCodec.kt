package com.mikai233.shared.codec

import com.mikai233.common.crypto.TEA
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec
import io.netty.util.AttributeKey

class CryptoCodec : ByteToMessageCodec<ByteArray>() {
    companion object {
        val cryptoKey = AttributeKey.valueOf<ByteArray>("TEA_KEA")
    }


    override fun encode(ctx: ChannelHandlerContext, packet: ByteArray, out: ByteBuf) {
        val key = ctx.channel().attr(cryptoKey).get()
        val maybeEncrypted = if (key != null) encrypt(key, packet) else packet
        val packetLen = Int.SIZE_BYTES + maybeEncrypted.size
        with(out) {
            writeInt(packetLen)
            writeBytes(maybeEncrypted)
        }
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (`in`.readableBytes() < Int.SIZE_BYTES) {
            return
        }
        `in`.markReaderIndex()
        val packetLen = `in`.readInt()
        if (packetLen - Int.SIZE_BYTES > `in`.readableBytes()) {
            `in`.resetReaderIndex()
            return
        }
        val encryptedBytes = ByteArray(packetLen - Int.SIZE_BYTES)
        `in`.readBytes(encryptedBytes)
        val key: ByteArray? = ctx.channel().attr(cryptoKey).get()
        val maybeDecrypted = if (key != null) decrypt(key, encryptedBytes) else encryptedBytes
        out.add(maybeDecrypted)
    }

    private fun encrypt(key: ByteArray, bytes: ByteArray): ByteArray {
        return TEA.encrypt(bytes, key)
    }

    private fun decrypt(key: ByteArray, bytes: ByteArray): ByteArray {
        return TEA.decrypt(bytes, key)
    }
}
