package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.crypto.AESCipher
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.protocol.idForServerMessage
import com.mikai233.protocol.parserForClientMessage
import io.github.mikai233.asteria.gateway.BinaryGatewayPacket
import io.github.mikai233.asteria.gateway.GatewayFrame
import io.github.mikai233.asteria.gateway.GatewaySession
import io.github.mikai233.asteria.gateway.GatewaySessionAttributeKey
import io.github.mikai233.asteria.gateway.IndexedBinaryGatewayPacketCodec

val GateCipherKey: GatewaySessionAttributeKey<AESCipher> = GatewaySessionAttributeKey("gate.cipher")
val GateSessionPacketCodecKey: GatewaySessionAttributeKey<IndexedBinaryGatewayPacketCodec> =
    GatewaySessionAttributeKey("gate.packetCodec")

class GateProtocolCodec {
    fun initialize(session: GatewaySession) {
        session.set(GateSessionPacketCodecKey, IndexedBinaryGatewayPacketCodec())
    }

    fun decodeClient(session: GatewaySession, frame: GatewayFrame): ClientProtobuf {
        val packet = packetCodec(session).decode(decrypt(session, frame))
        val parser = parserForClientMessage(packet.messageId)
        return ClientProtobuf(packet.messageId, parser.parseFrom(packet.payload))
    }

    fun encodeServer(
        session: GatewaySession,
        message: GeneratedMessage,
        encrypted: Boolean = true,
    ): GatewayFrame {
        val packet = BinaryGatewayPacket(
            messageId = idForServerMessage(message.javaClass),
            payload = message.toByteArray(),
        )
        val frame = packetCodec(session).encode(packet)
        return if (encrypted) encrypt(session, frame) else frame
    }

    private fun packetCodec(session: GatewaySession): IndexedBinaryGatewayPacketCodec {
        return requireNotNull(session.get(GateSessionPacketCodecKey)) {
            "gate packet codec not initialized for session ${session.id.value}"
        }
    }

    private fun decrypt(session: GatewaySession, frame: GatewayFrame): GatewayFrame {
        val cipher = session.get(GateCipherKey) ?: return frame
        return GatewayFrame(cipher.decrypt(frame.bytes))
    }

    private fun encrypt(session: GatewaySession, frame: GatewayFrame): GatewayFrame {
        val cipher = session.get(GateCipherKey) ?: return frame
        return GatewayFrame(cipher.encrypt(frame.bytes))
    }
}
