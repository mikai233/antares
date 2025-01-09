package com.mikai233.gate.server

import com.mikai233.common.extension.Platform
import com.mikai233.common.extension.getPlatform
import com.mikai233.common.extension.logger
import com.mikai233.gate.GateNode
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

class NettyServer(private val node: GateNode) : AutoCloseable {
    private val logger = logger()
    private val name: String = "netty-server"
    private val bossGroup: EventLoopGroup
    private val workGroup: EventLoopGroup

    init {
        when (getPlatform()) {
            Platform.Linux -> {
                bossGroup = EpollEventLoopGroup()
                workGroup = EpollEventLoopGroup()
            }

            Platform.MacOS -> {
                bossGroup = KQueueEventLoopGroup()
                workGroup = KQueueEventLoopGroup()
            }

            Platform.Windows, Platform.Unknown -> {
                bossGroup = NioEventLoopGroup()
                workGroup = NioEventLoopGroup()
            }
        }
        logger.info(
            "{} using bossGroup:{}, workGroup:{}",
            name,
            bossGroup.javaClass.simpleName,
            workGroup.javaClass.simpleName
        )
    }

    private fun stop() {
        bossGroup.shutdownGracefully().sync()
        workGroup.shutdownGracefully().sync()
    }

    fun start(): ChannelFuture {
        val bootstrap = ServerBootstrap()
        bootstrap
            .group(bossGroup, workGroup)
            .channel(getServerSocketChannel())
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(ServerChannelInitializer(node, null))
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)

        val nettyConfig = node.nettyConfig
        val future = bootstrap.bind(nettyConfig.host, nettyConfig.port).sync()
        logger.info("start netty server on host:{} port:{}", nettyConfig.host, nettyConfig.port)
        return future
    }

    private fun getServerSocketChannel(): Class<out ServerSocketChannel> {
        return when (getPlatform()) {
            Platform.Linux -> EpollServerSocketChannel::class.java
            Platform.MacOS -> KQueueServerSocketChannel::class.java
            Platform.Windows, Platform.Unknown -> NioServerSocketChannel::class.java
        }
    }

    override fun close() {
        stop()
    }
}
