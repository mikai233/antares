package com.mikai233.client

import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.crypto.KeyPair
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.protocol.loginReq
import io.netty.util.AttributeKey

class GameClient(host: String, port: Int) {
    private val client = NettyClient(host, port, ClientChannelInitializer())
    private val keyPair = ECDH.genKeyPair()

    companion object {
        val key = AttributeKey.valueOf<KeyPair>("KEY_PAIR")
    }

    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
    }

    fun start() {
        val loginReq = loginReq {
            account = "mikai233"
            worldId = 1000
            clientPublicKey = keyPair.publicKey.toByteString()
            clientZone = GlobalData.zoneId.toString()
        }
        val channel = client.startClient().sync().channel()
        channel.attr(key).set(keyPair)
        channel.writeAndFlush(loginReq)
    }
}
