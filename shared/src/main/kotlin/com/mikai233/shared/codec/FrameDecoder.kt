package com.mikai233.shared.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.TooLongFrameException

@Sharable
class FrameDecoder(private val maxFrameSize: Int) : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (`in`.readableBytes() < Int.SIZE_BYTES) {
            return
        }
        `in`.markReaderIndex()
        val frameSize = `in`.readInt()
        if (frameSize > maxFrameSize) {
            `in`.clear()
            throw TooLongFrameException("Frame size exceeds $maxFrameSize: $frameSize - discarded")
        }
        if (`in`.readableBytes() < frameSize) {
            `in`.resetReaderIndex()
            return
        }
        val bytes = ByteArray(frameSize)
        `in`.readBytes(bytes)
        out.add(bytes)
    }
}