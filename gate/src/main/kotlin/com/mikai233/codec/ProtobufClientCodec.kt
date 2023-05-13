package com.mikai233.codec

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.GlobalProto
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

@Sharable
class ProtobufClientCodec : MessageToMessageCodec<ByteArray, GeneratedMessageV3>() {
    override fun encode(ctx: ChannelHandlerContext, msg: GeneratedMessageV3, out: MutableList<Any>) {
        val protoId = GlobalProto.getServerMessageId(msg::class)

//        val message = when (msg) {
//            is MessageLite -> msg
//            is MessageLite.Builder -> msg.build()
//            else -> error("unsupported message type:${msg::class}")
//        }
//        val protoId =
//            requireNotNull(REQUEST_TYPE_2_ID[message::class]) { "message type:${message::class} not in ${::REQUEST_TYPE_2_ID.name} map" }
//        val bytes = message.toByteArray()
//        out.add(protoId.toByteArray() + bytes)
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteArray, out: MutableList<Any>) {
//        val protoId = msg.toInt()
//        val bytes = msg.sliceArray(PROTOBUF_HEADER_LEN until msg.size)
//        val parser =
//            requireNotNull(RESPONSE_ID_2_PARSER[protoId]) { "protoId:${protoId} not in ${::RESPONSE_ID_2_PARSER.name} map" }
//        val message = parser.parseFrom(bytes)
//        out.add(message)
    }
}