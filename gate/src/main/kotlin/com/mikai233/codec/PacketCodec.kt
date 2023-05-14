package com.mikai233.codec

import com.mikai233.common.ext.logger
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

class PacketCodec : ByteToMessageCodec<Packet>() {
    private val logger = logger()
    private var packetIndex: Int = 0
    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: ByteBuf) {
        val packetLen = msg.packetLen()
        with(out) {
            writeLong(packetLen.toLong())
            writeInt(packetIndex.toInt())
            writeLong(msg.compressedLen)
        }
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        `in`.markReaderIndex()
        if (`in`.readableBytes() < UInt.SIZE_BYTES) {
            `in`.markReaderIndex()
            return
        }
        val packetLen = `in`.readUnsignedInt()
        if (packetLen > `in`.readableBytes()) {
            `in`.resetReaderIndex()
            return
        }
        val clientPacketIndex = `in`.readUnsignedShort()
        if (packetIndex != clientPacketIndex) {
            logger.error("client packet index:{}!= server packet index:{}", clientPacketIndex, packetIndex)
            ctx.close()
        }
        val compressedLen = `in`.readUnsignedInt()
        val body = ByteArray(packetLen.toInt() - Packet.HEADER_LEN)
        `in`.readBytes(body)
        out.add(Packet(packetIndex, compressedLen, body))
        packetIndex = ++packetIndex % 65535
    }
}