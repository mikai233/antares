package com.mikai233.server

import com.mikai233.Gate
import com.mikai233.codec.CryptoCodec
import com.mikai233.codec.LZ4Codec
import com.mikai233.codec.ProtobufServerCodec
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContext

/**
 * |package length | encrypted                                data |
 * |----- Int -----|-------------------- data ---------------------|
 * |               | compressed length | compressed           data |
 * |----- Int -----|------- Int -------|---------- data -----------|
 * |               |                   | proto id | proto     data |
 * |----- Int -----|------- Int -------|-- Int ---|----- data -----|
 */
class ServerChannelInitializer(private val sslContext: SslContext? = null, private val gate: Gate) :
    ChannelInitializer<SocketChannel>() {
    private val lZ4Codec = LZ4Codec()
    private val protobufCodec = ProtobufServerCodec()
    private val channelHandler = ChannelHandler(gate)
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