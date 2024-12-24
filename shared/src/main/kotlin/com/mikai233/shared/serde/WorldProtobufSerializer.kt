package com.mikai233.shared.serde

import akka.serialization.JSerializer
import com.mikai233.shared.message.WorldProtobufEnvelope

class WorldProtobufSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        return WorldProtobufEnvelope(packetToProtoMsg(bytes, true))
    }

    override fun identifier(): Int {
        return 476961363
    }

    override fun toBinary(o: Any): ByteArray {
        o as WorldProtobufEnvelope
        return protoMsgToPacket(o.message, true)
    }

    override fun includeManifest(): Boolean {
        return false
    }
}
