package com.mikai233.shared.codec

import com.mikai233.common.ext.logger
import com.mikai233.common.ext.toByteArray
import com.mikai233.common.ext.toInt
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

class ServerPacketCodec : MessageToMessageCodec<ByteArray, Packet>() {
    private val logger = logger()
    private var packetIndex = 0

    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val packetBytes = packetIndex.toByteArray() +
                msg.protoId.toByteArray() +
                msg.originLen.toByteArray() +
                msg.body
        out.add(packetBytes)
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
        val clientPacketIndex = msg.toInt()
        if (packetIndex != clientPacketIndex) {
            logger.error("client packet index:{}!= server packet index:{}", clientPacketIndex, packetIndex)
            ctx.close()
        }
        val protoId = msg.toInt(Int.SIZE_BYTES)
        val originLen = msg.toInt(2 * Int.SIZE_BYTES)
        val body = msg.sliceArray(3 * Int.SIZE_BYTES until msg.size)
        out.add(Packet(packetIndex, protoId, originLen, body))
        packetIndex = ++packetIndex % 65535
    }
}
