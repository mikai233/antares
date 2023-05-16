package com.mikai233.shared.serde

import akka.serialization.JSerializer
import com.mikai233.shared.message.PlayerProtobufEnvelope

class PlayerProtobufSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        return PlayerProtobufEnvelope(packetToProtoMsg(bytes, true))
    }

    override fun identifier(): Int {
        return 247510507
    }

    override fun toBinary(o: Any): ByteArray {
        o as PlayerProtobufEnvelope
        return protoMsgToPacket(o.message, true)
    }

    override fun includeManifest(): Boolean {
        return false
    }
}