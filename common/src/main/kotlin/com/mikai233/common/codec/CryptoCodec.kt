package com.mikai233.common.codec

import com.mikai233.common.crypto.AESCipher
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.util.AttributeKey

val CIPHER_KEY: AttributeKey<AESCipher?> = AttributeKey.valueOf("AES_CIPHER")

@Sharable
class CryptoCodec : MessageToMessageCodec<ByteArray, ByteArray>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
        val cipher = ctx.channel().attr(CIPHER_KEY).get()
        val data = cipher?.encrypt(msg) ?: msg
        out.add(data)
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
        val cipher = ctx.channel().attr(CIPHER_KEY).get()
        val data = cipher?.decrypt(msg) ?: msg
        out.add(data)
    }
}
