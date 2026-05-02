package com.mikai233.common.codec

import com.mikai233.common.extension.logger
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

class PacketCodec : MessageToMessageCodec<ByteBuf, Packet>() {
    private val logger = logger()

    // 0-65535
    private var sendPacketIndex = 0
    private var recvPacketIndex = 0

    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val body = msg.body
        val byteBuf = ctx.alloc().buffer(3 * Int.SIZE_BYTES + body.readableBytes())
        byteBuf.writeInt(sendPacketIndex)
        byteBuf.writeInt(msg.protoId)
        byteBuf.writeInt(msg.originLen)
        byteBuf.writeBytes(body, body.readerIndex(), body.readableBytes())
        out.add(byteBuf)
        sendPacketIndex = ++sendPacketIndex % 65536
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val clientPacketIndex = msg.readInt()
        if (recvPacketIndex != clientPacketIndex) {
            logger.error("client packet index:{}!= expected recv packet index:{}", clientPacketIndex, recvPacketIndex)
            ctx.close()
            return
        }
        val protoId = msg.readInt()
        val originLen = msg.readInt()
        out.add(Packet(protoId, originLen, msg.readRetainedSlice(msg.readableBytes())))
        recvPacketIndex = ++recvPacketIndex % 65536
    }
}
