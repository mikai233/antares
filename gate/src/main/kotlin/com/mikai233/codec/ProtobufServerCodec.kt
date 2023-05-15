package com.mikai233.codec

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.GlobalProto
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

@Sharable
class ProtobufServerCodec : MessageToMessageCodec<Packet, GeneratedMessageV3>() {
    override fun encode(ctx: ChannelHandlerContext, msg: GeneratedMessageV3, out: MutableList<Any>) {
        val protoId = GlobalProto.getServerMessageId(msg::class)
        val body = msg.toByteArray()
        out.add(Packet(0, protoId, body.size, body))
    }

    override fun decode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val protoId = msg.protoId
        val parser = GlobalProto.getClientMessageParser(protoId)
        val protoMessage = parser.parseFrom(msg.body)
        out.add(protoMessage)
    }
}