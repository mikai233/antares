package com.mikai233.server

import com.mikai233.GateNode
import com.mikai233.codec.CryptoCodec
import com.mikai233.codec.LZ4Codec
import com.mikai233.codec.ProtobufServerCodec
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContext

/**
 * | packet  length | packet  index | proto id | compressed length | data |
 * |      UInt      |     UShort    |   UInt   |       UInt       |  ..  |
 */
class ServerChannelInitializer(private val sslContext: SslContext? = null, gateNode: GateNode) :
    ChannelInitializer<SocketChannel>() {
    private val lZ4Codec = LZ4Codec()
    private val protobufCodec = ProtobufServerCodec()
    private val channelHandler = ChannelHandler(gateNode)
    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            sslContext?.let {
                addLast(it.newHandler(ch.alloc()))
            }
            addLast(CryptoCodec())
            addLast(lZ4Codec)
            addLast(protobufCodec)
            addLast(channelHandler)
        }
    }
}