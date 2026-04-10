package com.mikai233.common.codec

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.protocol.idForServerMessage
import com.mikai233.protocol.parserForClientMessage
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

@Sharable
class ProtobufCodec : MessageToMessageCodec<Packet, GeneratedMessage>() {
    override fun encode(ctx: ChannelHandlerContext, msg: GeneratedMessage, out: MutableList<Any>) {
        val protoId = idForServerMessage(msg::class)
        val serializedSize = msg.serializedSize
        val body = ctx.alloc().buffer(serializedSize)
        ByteBufOutputStream(body).use(msg::writeTo)
        out.add(Packet(protoId, serializedSize, body))
    }

    override fun decode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        val protoId = msg.protoId
        val parser = parserForClientMessage(protoId)
        val message = ByteBufInputStream(msg.body.duplicate(), false).use(parser::parseFrom)
        out.add(ClientProtobuf(protoId, message))
    }
}
