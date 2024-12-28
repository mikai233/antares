package com.mikai233.shared.serde

import akka.serialization.JSerializer
import com.mikai233.shared.message.ProtobufEnvelopeToAllWorldClient
import com.mikai233.shared.message.ProtobufEnvelopeToWorldClient
import com.mikai233.shared.message.ServerProtobuf

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

class ProtobufEnvelopeToWorldClientSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        return ProtobufEnvelopeToAllWorldClient(packetToProtoMsg(bytes, false))
    }

    override fun identifier(): Int {
        return 404971523
    }

    override fun toBinary(o: Any): ByteArray {
        o as ProtobufEnvelopeToWorldClient
        return protoMsgToPacket(o.inner, false)
    }

    override fun includeManifest(): Boolean {
        return false
    }
}

class ProtobufEnvelopeToAllWorldClientSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        return ProtobufEnvelopeToAllWorldClient(packetToProtoMsg(bytes, false))
    }

    override fun identifier(): Int {
        return 137992592
    }

    override fun toBinary(o: Any): ByteArray {
        o as ProtobufEnvelopeToAllWorldClient
        return protoMsgToPacket(o.inner, false)
    }

    override fun includeManifest(): Boolean {
        return false
    }
}
