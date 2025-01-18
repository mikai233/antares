package com.mikai233.common.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.handler.codec.TooLongFrameException

/**
 * @param maxFrameSize The maximum length of the frame.
 */
@Sharable
class FrameCodec(private val maxFrameSize: Int) : MessageToMessageCodec<ByteBuf, ByteArray>() {
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
        if (`in`.readableBytes() < frameSize - Int.SIZE_BYTES) {
            `in`.resetReaderIndex()
            return
        }
        val bytes = ByteArray(frameSize - Int.SIZE_BYTES)
        `in`.readBytes(bytes)
        out.add(bytes)
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
        val frameSize = msg.size
        val byteBuf = ctx.alloc().buffer(Int.SIZE_BYTES + frameSize)
        byteBuf.writeInt(frameSize)
        byteBuf.writeBytes(msg)
        out.add(byteBuf)
    }
}
