package com.mikai233.codec

import com.mikai233.common.ext.toByteArray
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FastDecompressor

@ChannelHandler.Sharable
class LZ4Codec : MessageToMessageCodec<Pair<Int, ByteArray>, ByteArray>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
        val compressed = compressor().compress(msg)
        out.add(msg.size.toByteArray() + msg)
    }

    override fun decode(ctx: ChannelHandlerContext, msg: Pair<Int, ByteArray>, out: MutableList<Any>) {
        val originLen = msg.first
        val decompressed = decompressor().decompress(msg.second, originLen)
        out.add(msg.second)
    }

    private fun compressor(): LZ4Compressor {
        return LZ4Factory.fastestInstance().fastCompressor()
    }

    private fun decompressor(): LZ4FastDecompressor {
        return LZ4Factory.fastestInstance().fastDecompressor()
    }
}