package com.mikai233.client

import com.mikai233.client.codec.ConsoleHandler
import com.mikai233.shared.codec.*
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContext

class ClientChannelInitializer(private val sslContext: SslContext? = null) : ChannelInitializer<SocketChannel>() {
    private val lZ4Codec = LZ4Codec()
    private val protobufCodec = ProtobufClientCodec()
    private val consoleHandler = ConsoleHandler()
    private val exceptionHandler = ExceptionHandler()
    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            sslContext?.let {
                addLast(it.newHandler(ch.alloc()))
            }
            addLast(CryptoCodec())
            addLast(ClientPacketCodec())
            addLast(lZ4Codec)
            addLast(protobufCodec)
            addLast(consoleHandler)
            addLast(exceptionHandler)
        }
    }
}
