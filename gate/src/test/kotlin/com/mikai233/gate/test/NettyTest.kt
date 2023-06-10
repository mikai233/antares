package com.mikai233.gate.test

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.protobufJsonPrinter
import com.mikai233.protocol.MsgCs.MessageClientToServer
import com.mikai233.protocol.MsgSc.MessageServerToClient
import com.mikai233.protocol.loginReq
import com.mikai233.protocol.loginResp
import com.mikai233.shared.codec.*
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import org.junit.jupiter.api.Test

class NettyTest {
    class FakeClientChannelHandler() : ChannelInboundHandlerAdapter() {
        private val logger = logger()
        private val printer = protobufJsonPrinter()

        override fun channelActive(ctx: ChannelHandlerContext) {
            logger.info("client channelActive")
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            msg as GeneratedMessageV3
            logger.info("{}", printer.print(msg))
        }
    }

    class FakeServerChannelHandler() : ChannelInboundHandlerAdapter() {
        private val logger = logger()
        private val printer = protobufJsonPrinter()

        override fun channelActive(ctx: ChannelHandlerContext) {
            logger.info("server channelActive")
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            msg as GeneratedMessageV3
            logger.info("{}", printer.print(msg))
            ctx.writeAndFlush(loginResp { })
        }
    }

    @Test
    fun testCodec() {
        GlobalProto.init(MessageClientToServer.getDescriptor(), MessageServerToClient.getDescriptor())
        val client = clientBootstrap()
        val server = serverBootstrap()
        val serverChannel = server.bind(6789).sync().channel()
        val clientChannel = client.connect("localhost", 6789).sync()
        repeat(10000) {
            clientChannel.channel().writeAndFlush(loginReq { })
        }
//        serverChannel.closeFuture().sync()
    }

    private fun serverBootstrap(): ServerBootstrap {
        val bootstrap = ServerBootstrap()
        bootstrap
            .group(NioEventLoopGroup(), NioEventLoopGroup())
            .channel(NioServerSocketChannel::class.java)
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    with(ch.pipeline()) {
                        addLast(ServerPacketCodec())
                        addLast(LZ4Codec())
                        addLast(ProtobufServerCodec())
                        addLast(FakeServerChannelHandler())
                        addLast(ExceptionHandler())
                    }
                }
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
        return bootstrap
    }

    private fun clientBootstrap(): Bootstrap {
        val bootstrap = Bootstrap()
        bootstrap
            .group(NioEventLoopGroup())
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    with(ch.pipeline()) {
                        addLast(ClientPacketCodec())
                        addLast(LZ4Codec())
                        addLast(ProtobufClientCodec())
                        addLast(FakeClientChannelHandler())
                        addLast(ExceptionHandler())
                    }
                }
            })
        return bootstrap
    }

    @Test
    fun testClient() {
        val client = clientBootstrap()
        val channel = client.connect("localhost", 6666).sync().channel()
        channel.writeAndFlush(loginReq { })
    }
}
