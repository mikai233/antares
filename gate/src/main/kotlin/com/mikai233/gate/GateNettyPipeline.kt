package com.mikai233.gate

import com.mikai233.common.codec.CIPHER_KEY
import com.mikai233.common.codec.CryptoCodec
import com.mikai233.common.codec.ExceptionHandler
import com.mikai233.common.codec.FrameCodec
import com.mikai233.common.codec.LZ4Codec
import com.mikai233.common.codec.PacketCodec
import com.mikai233.common.codec.ProtobufCodec
import com.mikai233.common.message.ClientProtobuf
import io.github.mikai233.asteria.gateway.GatewayCloseReason
import io.github.mikai233.asteria.gateway.GatewayFrame
import io.github.mikai233.asteria.gateway.GatewaySession
import io.github.mikai233.asteria.gateway.GatewaySessionAttributeKey
import io.github.mikai233.asteria.gateway.netty.NettyGatewayPipelineContext
import io.github.mikai233.asteria.gateway.netty.NettyGatewayPipelineInstaller
import io.github.mikai233.asteria.gateway.netty.NettyGatewayFrameWriter
import io.netty.channel.Channel
import io.netty.channel.socket.SocketChannel

val GateNettyChannelKey: GatewaySessionAttributeKey<Channel> = GatewaySessionAttributeKey("gate.nettyChannel")

object GateNettyPipeline {
    fun installer(protocolCodec: GateProtocolCodec): NettyGatewayPipelineInstaller {
        return NettyGatewayPipelineInstaller { channel, context ->
            channel.installGatePipeline(context, protocolCodec)
        }
    }

    private fun SocketChannel.installGatePipeline(
        context: NettyGatewayPipelineContext,
        protocolCodec: GateProtocolCodec,
    ) {
        pipeline()
            .addLast(FrameCodec(context.options.maxFrameLength))
            .addLast(CryptoCodec())
            .addLast(PacketCodec())
            .addLast(LZ4Codec())
            .addLast(ProtobufCodec())
            .addLast(context.gatewayMessageHandler(
                inboundType = ClientProtobuf::class.java,
                receiver = { messageContext ->
                    messageContext.session.set(GateNettyChannelKey, messageContext.context.channel())
                    context.handler.received(
                        messageContext.session,
                        protocolCodec.encodeClient(messageContext.message),
                    )
                },
                writer = NettyGatewayFrameWriter { channel, frame ->
                    channel.writeAndFlush(protocolCodec.decodeServer(frame))
                },
            ))
            .addLast(ExceptionHandler())
    }
}

fun GatewaySession.enableGateCipher(cipher: com.mikai233.common.crypto.AESCipher) {
    requireNotNull(get(GateNettyChannelKey)) { "gate Netty channel not bound for session ${id.value}" }
        .attr(CIPHER_KEY)
        .set(cipher)
}

fun GatewaySession.closeGateChannel() {
    close(GatewayCloseReason.Application)
}
