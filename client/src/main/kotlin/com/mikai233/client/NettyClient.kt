package com.mikai233.client

import com.mikai233.common.extension.Platform
import com.mikai233.common.extension.getPlatform
import com.mikai233.common.extension.logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel

class NettyClient(
    private val host: String,
    private val port: Int,
    private val channelInitializer: ChannelInitializer<SocketChannel>,
    name: String = "netty-client",
) {
    private val logger = logger()
    private val bossGroup: EventLoopGroup = when (getPlatform()) {
        Platform.Linux -> {
            EpollEventLoopGroup()
        }

        Platform.MacOS -> {
            KQueueEventLoopGroup()
        }

        Platform.Windows, Platform.Unknown -> {
            NioEventLoopGroup()
        }
    }

    init {
        logger.info("{} using bossGroup:{} ", name, bossGroup.javaClass.simpleName)
    }

    fun startClient(): ChannelFuture {
        val bootstrap = Bootstrap()
        bootstrap.group(bossGroup).channel(getSocketChannel()).handler(channelInitializer)
        val future = bootstrap.connect(host, port).also { future ->
            future.sync()
        }
        logger.info("start netty client on host:{}, port:{}", host, port)
        return future
    }

    private fun getSocketChannel(): Class<out SocketChannel> {
        return when (getPlatform()) {
            Platform.Linux -> EpollSocketChannel::class.java
            Platform.MacOS -> KQueueSocketChannel::class.java
            Platform.Windows, Platform.Unknown -> NioSocketChannel::class.java
        }
    }
}
