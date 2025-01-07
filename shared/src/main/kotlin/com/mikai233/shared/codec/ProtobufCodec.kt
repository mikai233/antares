package com.mikai233.shared.codec

import com.google.protobuf.GeneratedMessage
import com.mikai233.protocol.idForServerMessage
import com.mikai233.protocol.parserForClientMessage
import com.mikai233.shared.message.ClientProtobuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

@Sharable
class ProtobufCodec : MessageToMessageCodec<Packet, GeneratedMessage>() {
    override fun encode(ctx: ChannelHandlerContext, msg: GeneratedMessage, out: MutableList<Any>) {
        val protoId = idForServerMessage(msg::class)
        val body = msg.toByteArray()
        out.add(Packet(protoId, body.size, body))
    }

    override fun decode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val protoId = msg.protoId
        val parser = parserForClientMessage(protoId)
        val message = parser.parseFrom(msg.body)
        out.add(ClientProtobuf(protoId, message))
    }
}
