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
import kotlin.concurrent.thread

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

    fun start(): Thread {
        return thread(name = name, isDaemon = true) {
            try {
                val futures = startServer()
                for (future in futures) {
                    future.channel().closeFuture().sync()
                    logger.info("channel:{} closed", future.channel())
                }
            } catch (e: Exception) {
                logger.error("", e)
            } finally {
                stop()
            }
        }
    }

    private fun stop() {
        bossGroup.shutdownGracefully().sync()
        workGroup.shutdownGracefully().sync()
    }

    private fun startServer(): List<ChannelFuture> {
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

        val ports = listOf(node.nettyConfig.port)
        val futures = ports.map {
            bootstrap.bind(it).also { future ->
                future.sync()
            }
        }
        logger.info("start netty server on port:{}", ports)
        return futures
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
