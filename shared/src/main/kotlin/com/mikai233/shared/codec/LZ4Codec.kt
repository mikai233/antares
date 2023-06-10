package com.mikai233.shared.codec

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FastDecompressor

@ChannelHandler.Sharable
class LZ4Codec : MessageToMessageCodec<Packet, Packet>() {
    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val body = compressor().compress(msg.body)
        msg.body = body
        out.add(msg)
    }

    override fun decode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val body = ByteArray(msg.originLen)
        decompressor().decompress(msg.body, body)
        msg.body = body
        out.add(msg)
    }

    private fun compressor(): LZ4Compressor {
        return LZ4Factory.fastestInstance().fastCompressor()
    }

    private fun decompressor(): LZ4FastDecompressor {
        return LZ4Factory.fastestInstance().fastDecompressor()
    }
}
