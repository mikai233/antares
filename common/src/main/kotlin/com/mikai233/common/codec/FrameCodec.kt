package com.mikai233.common.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.handler.codec.TooLongFrameException

/**
 * @param maxFrameSize The maximum length of the frame.
 */
@Sharable
class FrameCodec(private val maxFrameSize: Int) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (`in`.readableBytes() < Int.SIZE_BYTES) {
            return
        }
        `in`.markReaderIndex()
        val frameSize = `in`.readInt()
        if (frameSize < Int.SIZE_BYTES) {
            `in`.clear()
            throw CorruptedFrameException("Frame size must be at least ${Int.SIZE_BYTES}: $frameSize")
        }
        if (frameSize > maxFrameSize) {
            `in`.clear()
            throw TooLongFrameException("Frame size exceeds $maxFrameSize: $frameSize - discarded")
        }
        if (`in`.readableBytes() < frameSize - Int.SIZE_BYTES) {
            `in`.resetReaderIndex()
            return
        }
        out.add(`in`.readRetainedSlice(frameSize - Int.SIZE_BYTES))
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val frameSize = Int.SIZE_BYTES + msg.readableBytes()
        val header = ctx.alloc().buffer(Int.SIZE_BYTES)
        header.writeInt(frameSize)
        val composite = ctx.alloc().compositeBuffer(2)
        composite.addComponents(true, header, msg.retainedDuplicate())
        out.add(composite)
    }
}
