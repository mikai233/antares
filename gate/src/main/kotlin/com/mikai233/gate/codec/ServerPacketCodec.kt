package com.mikai233.gate.codec

import com.mikai233.common.ext.logger
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

class ServerPacketCodec : ByteToMessageCodec<Packet>() {
    private val logger = logger()
    private var packetIndex = 0
    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: ByteBuf) {
        with(out) {
            writeInt(msg.packetLen())
            writeInt(packetIndex)
            writeInt(msg.protoId)
            writeInt(msg.originLen)
            writeBytes(msg.body)
        }
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        `in`.markReaderIndex()
        if (`in`.readableBytes() < Int.SIZE_BYTES) {
            `in`.markReaderIndex()
            return
        }
        val packetLen = `in`.readInt()
        if (packetLen - Packet.PACKET_LEN > `in`.readableBytes()) {
            `in`.resetReaderIndex()
            return
        }
        val clientPacketIndex = `in`.readInt()
        if (packetIndex != clientPacketIndex) {
            logger.error("client packet index:{}!= server packet index:{}", clientPacketIndex, packetIndex)
            ctx.close()
        }
        val protoId = `in`.readInt()
        val originLen = `in`.readInt()
        val body = ByteArray(packetLen - Packet.HEADER_LEN)
        `in`.readBytes(body)
        out.add(Packet(packetIndex, protoId, originLen, body))
        packetIndex = ++packetIndex % 65535
    }
}
