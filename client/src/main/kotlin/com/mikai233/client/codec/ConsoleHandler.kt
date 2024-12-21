package com.mikai233.client.codec

import com.mikai233.client.GameClient
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.ProtoSystem.PingResp
import com.mikai233.protocol.pingReq
import com.mikai233.shared.codec.CryptoCodec
import com.mikai233.shared.logMessage
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class ConsoleHandler : ChannelInboundHandlerAdapter() {
    private val logger = logger()
    override fun channelRead(ctx: ChannelHandlerContext, resp: Any) {
        when (resp) {
            is LoginResp -> {
                val keyPair = ctx.channel().attr(GameClient.key).get()
                val shareKey = ECDH.calculateShareKey(keyPair.privateKey, resp.serverPublicKey.toByteArray())
                ctx.channel().attr(CryptoCodec.cryptoKey).set(shareKey)
                thread {
                    while (true) {
                        ctx.writeAndFlush(pingReq {
                            clientTimestamp = unixTimestamp()
                        })
                        Thread.sleep(1000)
                    }
                }
            }

            !is PingResp -> {
                logMessage(logger, resp)
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger.info("session closed by server")
        exitProcess(0)
    }
}
