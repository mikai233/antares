package com.mikai233.server

import com.mikai233.GateSystemMessage
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Cluster
import com.mikai233.common.core.components.Component
import com.mikai233.common.ext.Platform
import com.mikai233.common.ext.getPlatform
import com.mikai233.common.ext.logger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import kotlin.concurrent.thread

class NettyServer : Component {
    private val logger = logger()
    private lateinit var server: Server
    private lateinit var cluster: Cluster<GateSystemMessage>
    private lateinit var host: String
    private lateinit var ports: IntArray
    lateinit var channelInitializer: ChannelInitializer<SocketChannel>
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
            "{} using bossGroup:{}, workGroup:{}", name, bossGroup.javaClass.simpleName, workGroup.javaClass.simpleName
        )
    }

    override fun init(server: Server) {
        this.server = server
        cluster = server.component()
    }

    override fun shutdown() {

    }

    fun initHostAndPorts() {

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
                bossGroup.shutdownGracefully()
                workGroup.shutdownGracefully()
            }
        }
    }

    private fun startServer(): List<ChannelFuture> {
        val bootstrap = ServerBootstrap()
        bootstrap
            .group(bossGroup, workGroup)
            .channel(getServerSocketChannel())
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(channelInitializer)
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
        val futures = ports.map {
            bootstrap.bind(it).also { future ->
                future.sync()
            }
        }
        logger.info("start netty server on host:{}, port:{}", host, ports)
        return futures
    }

    private fun getServerSocketChannel(): Class<out ServerSocketChannel> {
        return when (getPlatform()) {
            Platform.Linux -> EpollServerSocketChannel::class.java
            Platform.MacOS -> KQueueServerSocketChannel::class.java
            Platform.Windows, Platform.Unknown -> NioServerSocketChannel::class.java
        }
    }
}