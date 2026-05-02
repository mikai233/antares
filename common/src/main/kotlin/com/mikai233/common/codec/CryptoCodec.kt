package com.mikai233.common.codec

import com.mikai233.common.crypto.AESCipher
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.util.AttributeKey

val CIPHER_KEY: AttributeKey<AESCipher?> = AttributeKey.valueOf("AES_CIPHER")

@Sharable
class CryptoCodec : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val cipher = ctx.channel().attr(CIPHER_KEY).get()
        if (cipher == null) {
            out.add(msg.retain())
            return
        }
        out.add(Unpooled.wrappedBuffer(cipher.encrypt(ByteBufUtil.getBytes(msg))))
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val cipher = ctx.channel().attr(CIPHER_KEY).get()
        if (cipher == null) {
            out.add(msg.retain())
            return
        }
        out.add(Unpooled.wrappedBuffer(cipher.decrypt(ByteBufUtil.getBytes(msg))))
    }
}
