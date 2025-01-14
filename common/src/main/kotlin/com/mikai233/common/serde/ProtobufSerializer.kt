package com.mikai233.common.serde

import akka.serialization.JSerializer
import com.mikai233.common.extension.toInt
import com.mikai233.common.message.ProtobufEnvelope
import com.mikai233.protocol.idForClientMessage
import com.mikai233.protocol.parserForClientMessage
import java.nio.ByteBuffer

class ProtobufSerializer : JSerializer() {
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        val buffer = ByteBuffer.wrap(bytes)
        bytes.toInt()
        val id = buffer.long
        val protoId = buffer.int
        val messageBytes = ByteArray(buffer.remaining())
        buffer.get(messageBytes)
        val parser = parserForClientMessage(protoId)
        val message = parser.parseFrom(messageBytes)
        return ProtobufEnvelope(id, message)
    }

    override fun identifier(): Int {
        return 247510507
    }

    override fun toBinary(o: Any): ByteArray {
        val envelope = o as ProtobufEnvelope
        val message = envelope.message
        val messageBytes = message.toByteArray()
        val protoId = idForClientMessage(message::class)
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES + Int.SIZE_BYTES + messageBytes.size)
        buffer.putLong(envelope.id).putInt(protoId).put(messageBytes)
        return if (buffer.hasArray()) {
            buffer.array()
        } else {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes
        }
    }

    override fun includeManifest(): Boolean {
        return false
    }
}
