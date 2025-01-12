package com.mikai233.common.serde

import akka.serialization.JSerializer
import com.mikai233.common.message.ServerProtobuf

class ChannelProtobufSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        return ServerProtobuf(packetToProtoMsg(bytes, false))
    }

    override fun identifier(): Int {
        return 903794872
    }

    override fun toBinary(o: Any): ByteArray {
        o as ServerProtobuf
        return protoMsgToPacket(o.message, false)
    }

    override fun includeManifest(): Boolean {
        return false
    }
}
