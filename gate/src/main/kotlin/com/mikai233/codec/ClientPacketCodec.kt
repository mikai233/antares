package com.mikai233.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

class ClientPacketCodec : ByteToMessageCodec<Packet>() {
    private var packetIndex = 0
    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: ByteBuf) {
        with(out) {
            writeInt(msg.packetLen())
            writeInt(packetIndex)
            writeInt(msg.protoId)
            writeInt(msg.originLen)
            writeBytes(msg.body)
        }
        packetIndex = ++packetIndex % 65535
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        `in`.markReaderIndex()
        if (`in`.readableBytes() < Int.SIZE_BYTES) {
            `in`.markReaderIndex()
            return
        }
        val packetLen = `in`.readInt()
        if (packetLen > `in`.readableBytes()) {
            `in`.resetReaderIndex()
            return
        }
        `in`.readInt()//index
        val protoId = `in`.readInt()
        val originLen = `in`.readInt()
        val body = ByteArray(packetLen - Packet.HEADER_LEN)
        `in`.readBytes(body)
        out.add(Packet(packetIndex, protoId, originLen, body))
    }
}