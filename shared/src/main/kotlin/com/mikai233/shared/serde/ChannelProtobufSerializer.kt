package com.mikai233.shared.serde

import akka.serialization.JSerializer
import com.mikai233.shared.message.ChannelProtobufEnvelope

class ChannelProtobufSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        return ChannelProtobufEnvelope(packetToProtoMsg(bytes, false))
    }

    override fun identifier(): Int {
        return 903794872
    }

    override fun toBinary(o: Any): ByteArray {
        o as ChannelProtobufEnvelope
        return protoMsgToPacket(o.inner, false)
    }

    override fun includeManifest(): Boolean {
        return false
    }
}