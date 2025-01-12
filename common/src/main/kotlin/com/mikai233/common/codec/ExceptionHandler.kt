package com.mikai233.common.codec

import com.mikai233.common.extension.logger
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import java.net.SocketAddress
import java.net.SocketException

@Sharable
class ExceptionHandler : ChannelDuplexHandler() {
    private val logger = logger()

    override fun connect(
        ctx: ChannelHandlerContext,
        remoteAddress: SocketAddress?,
        localAddress: SocketAddress?,
        promise: ChannelPromise
    ) {
        ctx.connect(
            remoteAddress, promise.addListener(ChannelFutureListener {
                if (!it.isSuccess) {
                    logger.error("", it.cause())
                }
            })
        )
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        ctx.write(msg, promise.addListener(ChannelFutureListener {
            if (!it.isSuccess) {
                logger.error("", it.cause())
            }
        }))
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause !is SocketException) {
            logger.error("", cause)
        }
        ctx.close()
    }
}
