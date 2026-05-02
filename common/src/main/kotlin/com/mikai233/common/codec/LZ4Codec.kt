package com.mikai233.common.codec

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FastDecompressor

@ChannelHandler.Sharable
class LZ4Codec : MessageToMessageCodec<Packet, Packet>() {
    private val compressor: LZ4Compressor = LZ4Factory.fastestInstance().fastCompressor()
    private val decompressor: LZ4FastDecompressor = LZ4Factory.fastestInstance().fastDecompressor()

    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val compressed = compressor.compress(ByteBufUtil.getBytes(msg.body))
        out.add(Packet(msg.protoId, msg.body.readableBytes(), Unpooled.wrappedBuffer(compressed)))
    }

    override fun decode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val body = ByteArray(msg.originLen)
        decompressor.decompress(ByteBufUtil.getBytes(msg.body), body)
        out.add(Packet(msg.protoId, msg.originLen, Unpooled.wrappedBuffer(body)))
    }
}
