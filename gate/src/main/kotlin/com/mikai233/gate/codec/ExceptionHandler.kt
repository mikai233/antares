package com.mikai233.gate.codec

import com.mikai233.common.ext.logger
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import java.net.SocketAddress

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
}
