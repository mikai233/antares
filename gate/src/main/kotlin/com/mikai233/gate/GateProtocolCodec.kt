package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.protocol.idForServerMessage
import com.mikai233.protocol.parserForClientMessage
import com.mikai233.protocol.parserForServerMessage
import io.github.mikai233.asteria.gateway.GatewayFrame
import java.nio.ByteBuffer

/**
 * Internal adapter between Asteria GatewayFrame and this game's protobuf envelope.
 *
 * This is not the client wire format. The wire format is still owned by the Netty pipeline:
 * FrameCodec -> CryptoCodec -> PacketCodec -> LZ4Codec -> ProtobufCodec.
 */
class GateProtocolCodec {
    fun encodeClient(message: ClientProtobuf): GatewayFrame {
        return encode(message.id, message.message.toByteArray())
    }

    fun decodeClient(frame: GatewayFrame): ClientProtobuf {
        val (id, payload) = decode(frame)
        val parser = parserForClientMessage(id)
        return ClientProtobuf(id, parser.parseFrom(payload))
    }

    fun encodeServer(message: GeneratedMessage): GatewayFrame {
        return encode(idForServerMessage(message.javaClass), message.toByteArray())
    }

    fun decodeServer(frame: GatewayFrame): GeneratedMessage {
        val (id, payload) = decode(frame)
        val parser = parserForServerMessage(id)
        return parser.parseFrom(payload)
    }

    private fun encode(id: Int, payload: ByteArray): GatewayFrame {
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + payload.size)
        buffer.putInt(id)
        buffer.put(payload)
        return GatewayFrame(buffer.array())
    }

    private fun decode(frame: GatewayFrame): Pair<Int, ByteArray> {
        val buffer = ByteBuffer.wrap(frame.bytes)
        require(buffer.remaining() >= Int.SIZE_BYTES) { "gate protobuf frame is too short" }
        val id = buffer.int
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)
        return id to payload
    }
}
