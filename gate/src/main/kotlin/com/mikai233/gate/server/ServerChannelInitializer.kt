package com.mikai233.gate.server

import com.mikai233.common.inject.XKoin
import com.mikai233.gate.codec.ExceptionHandler
import com.mikai233.gate.codec.LZ4Codec
import com.mikai233.gate.codec.ProtobufServerCodec
import com.mikai233.gate.codec.ServerPacketCodec
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContext

/**
 * | packet  length | packet  index | proto id | origin length | data |
 * |       Int      |      Int     |    Int   |       Int     |  ..  |
 */
class ServerChannelInitializer(koin: XKoin, private val sslContext: SslContext? = null) :
    ChannelInitializer<SocketChannel>() {
    private val lZ4Codec = LZ4Codec()
    private val protobufCodec = ProtobufServerCodec()
    private val channelHandler = ChannelHandler(koin)
    private val exceptionHandler = ExceptionHandler()
    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            sslContext?.let {
                addLast(it.newHandler(ch.alloc()))
            }
//            addLast(CryptoCodec())
            addLast(ServerPacketCodec())
            addLast(lZ4Codec)
            addLast(protobufCodec)
            addLast(channelHandler)
            addLast(exceptionHandler)
        }
    }
}
