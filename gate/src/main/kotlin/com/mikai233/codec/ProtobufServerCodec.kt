package com.mikai233.codec

import com.google.protobuf.MessageLiteOrBuilder
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

@Sharable
class ProtobufServerCodec : MessageToMessageCodec<ByteArray, MessageLiteOrBuilder>() {
    override fun encode(ctx: ChannelHandlerContext, msg: MessageLiteOrBuilder, out: MutableList<Any>) {
//        val message = when (msg) {
//            is MessageLite -> msg
//            is MessageLite.Builder -> msg.build()
//            else -> error("unsupported message type:${msg.javaClass}")
//        }
//        val protoId =
//            requireNotNull(RESPONSE_TYPE_2_ID[message::class]) { "message type:${message::class} not in ${::RESPONSE_TYPE_2_ID.name} map" }
//        val bytes = message.toByteArray()
//        out.add(protoId.toByteArray() + bytes)
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
//        val protoId = msg.toInt()
//        val bytes = msg.sliceArray(PROTOBUF_HEADER_LEN until msg.size)
//        val parser =
//            requireNotNull(REQUEST_ID_2_PARSER[protoId]) { "protoId:${protoId} not in ${::REQUEST_ID_2_PARSER.name} map" }
//        val message = parser.parseFrom(bytes)
//        out.add(message)
    }
}