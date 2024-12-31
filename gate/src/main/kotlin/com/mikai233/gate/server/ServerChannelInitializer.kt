package com.mikai233.gate.server

import com.mikai233.gate.GateNode
import com.mikai233.shared.codec.*
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContext

/**
 * | packet  length | packet  index | proto id | origin length | data |
 * |       Int      |      Int     |    Int   |       Int     |  ..  |
 */
class ServerChannelInitializer(node: GateNode, private val sslContext: SslContext? = null) :
    ChannelInitializer<SocketChannel>() {
    //100k
    private val frameCodec = FrameCodec(1024 * 100)
    private val cryptoCodec = CryptoCodec()
    private val lZ4Codec = LZ4Codec()
    private val protobufCodec = ProtobufCodec()
    private val channelHandler = ChannelHandler(node)
    private val exceptionHandler = ExceptionHandler()
    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            sslContext?.let {
                addLast(it.newHandler(ch.alloc()))
            }
            addLast(frameCodec)
            addLast(cryptoCodec)
            addLast(PacketCodec())
            addLast(lZ4Codec)
            addLast(protobufCodec)
            addLast(channelHandler)
            addLast(exceptionHandler)
        }
    }
}
