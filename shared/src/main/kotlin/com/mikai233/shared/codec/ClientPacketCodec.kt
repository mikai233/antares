package com.mikai233.shared.codec

import com.mikai233.common.extension.toByteArray
import com.mikai233.common.extension.toInt
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

class ClientPacketCodec : MessageToMessageCodec<ByteArray, Packet>() {
    private var packetIndex = 0

    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val packetBytes = packetIndex.toByteArray() +
                msg.protoId.toByteArray() +
                msg.originLen.toByteArray() +
                msg.body
        out.add(packetBytes)
        packetIndex = ++packetIndex % 65535
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
        //ignore index
        val protoId = msg.toInt(Int.SIZE_BYTES)
        val originLen = msg.toInt(2 * Int.SIZE_BYTES)
        val body = msg.sliceArray(3 * Int.SIZE_BYTES until msg.size)
        out.add(Packet(packetIndex, protoId, originLen, body))
    }
}
