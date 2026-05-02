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
import io.github.mikai233.asteria.gateway.GatewayConnection
import io.github.mikai233.asteria.gateway.GatewayConnectionId
import io.github.mikai233.asteria.gateway.GatewayFrame
import io.github.mikai233.asteria.gateway.GatewaySession
import io.github.mikai233.asteria.gateway.GatewaySessionAttributeKey
import io.github.mikai233.asteria.gateway.GatewayTransportKind
import io.github.mikai233.asteria.gateway.netty.NettyGatewayPipelineContext
import io.github.mikai233.asteria.gateway.netty.NettyGatewayPipelineInstaller
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.net.SocketAddress

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
            .addLast(GateNettyBridgeHandler(context, protocolCodec))
            .addLast(ExceptionHandler())
    }
}

private class GateNettyBridgeHandler(
    private val gatewayContext: NettyGatewayPipelineContext,
    private val protocolCodec: GateProtocolCodec,
) : SimpleChannelInboundHandler<ClientProtobuf>() {
    private var session: Deferred<GatewaySession>? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        val connection = GateNettyConnection(
            id = gatewayContext.connectionIdFactory(),
            transport = gatewayContext.transport,
            channel = ctx.channel(),
            protocolCodec = protocolCodec,
        )
        session = gatewayContext.scope.async {
            gatewayContext.handler.connected(connection).also {
                it.set(GateNettyChannelKey, ctx.channel())
            }
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ClientProtobuf) {
        val activeSession = session ?: run {
            ctx.close()
            return
        }
        gatewayContext.scope.launch {
            runCatching {
                gatewayContext.handler.received(activeSession.await(), protocolCodec.encodeClient(msg))
            }.onFailure {
                gatewayContext.handler.disconnected(activeSession.await(), it)
                ctx.close()
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val activeSession = session ?: return
        gatewayContext.scope.launch {
            runCatching {
                gatewayContext.handler.disconnected(activeSession.await())
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val activeSession = session
        if (activeSession != null) {
            gatewayContext.scope.launch {
                runCatching {
                    gatewayContext.handler.disconnected(activeSession.await(), cause)
                }
            }
        }
        ctx.close()
    }
}

private class GateNettyConnection(
    override val id: GatewayConnectionId,
    override val transport: GatewayTransportKind,
    private val channel: Channel,
    private val protocolCodec: GateProtocolCodec,
) : GatewayConnection {
    override val remoteAddress: SocketAddress?
        get() = channel.remoteAddress()

    override fun write(frame: GatewayFrame) {
        channel.writeAndFlush(protocolCodec.decodeServer(frame))
    }

    override fun close() {
        channel.close()
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
