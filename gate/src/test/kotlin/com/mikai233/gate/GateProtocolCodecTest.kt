package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.crypto.AESCipher
import com.mikai233.protocol.CSEnum
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.loginReq
import com.mikai233.protocol.loginResp
import com.mikai233.protocol.parserForServerMessage
import com.mikai233.protocol.playerData
import com.mikai233.protocol.testNotify
import io.github.mikai233.asteria.gateway.GatewayConnection
import io.github.mikai233.asteria.gateway.GatewayConnectionId
import io.github.mikai233.asteria.gateway.GatewayFrame
import io.github.mikai233.asteria.gateway.GatewaySession
import io.github.mikai233.asteria.gateway.GatewaySessionId
import io.github.mikai233.asteria.gateway.GatewayTransportKind
import io.github.mikai233.asteria.gateway.IndexedBinaryGatewayPacketCodec
import io.github.mikai233.asteria.gateway.BinaryGatewayPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.SocketAddress

class GateProtocolCodecTest {
    @Test
    fun testServerMessageRoundTripWithoutCrypto() {
        roundTripServerMessages()
    }

    @Test
    fun testServerMessageRoundTripWithCrypto() {
        roundTripServerMessages(AESCipher("1234567890123456"))
    }

    @Test
    fun testClientMessageDecodeWithAsteriaPacketCodec() {
        val session = newSession()
        val codec = GateProtocolCodec()
        codec.initialize(session)
        val clientPackets = IndexedBinaryGatewayPacketCodec()
        val loginReq = loginReq {
            worldId = 1
            clientPublicKey = "client-key".toByteArray().toByteString()
        }
        val frame = clientPackets.encode(BinaryGatewayPacket(CSEnum.LoginReq.id, loginReq.toByteArray()))

        val decoded = codec.decodeClient(session, frame)

        assertEquals(CSEnum.LoginReq.id, decoded.id)
        assertEquals(loginReq, decoded.message)
    }

    private fun roundTripServerMessages(cipher: AESCipher? = null) {
        val session = newSession()
        val codec = GateProtocolCodec()
        codec.initialize(session)
        cipher?.let { session.set(GateCipherKey, it) }

        val messages = listOf(
            testNotify {
                data = "round-trip"
            },
            loginResp {
                result = ProtoLogin.LoginResult.Success
                data = playerData {
                    playerId = 1001
                    nickname = "codec-test"
                }
                serverZone = "test-zone"
                serverPublicKey = "server-public-key".toByteArray().toByteString()
            },
        )
        val clientPackets = IndexedBinaryGatewayPacketCodec()

        messages.forEach { message ->
            val encoded = codec.encodeServer(session, message)
            val frame = if (cipher == null) encoded else GatewayFrame(cipher.decrypt(encoded.bytes))
            val packet = clientPackets.decode(frame)
            val decoded = decodeServerMessage(packet)
            assertEquals(message, decoded)
        }
    }

    private fun decodeServerMessage(packet: BinaryGatewayPacket): GeneratedMessage {
        val parser = parserForServerMessage(packet.messageId)
        return parser.parseFrom(packet.payload)
    }

    private fun newSession(): GatewaySession {
        return GatewaySession(GatewaySessionId("test-session"), TestGatewayConnection)
    }

    private object TestGatewayConnection : GatewayConnection {
        override val id: GatewayConnectionId = GatewayConnectionId("test-connection")
        override val transport: GatewayTransportKind = GatewayTransportKind.TCP
        override val remoteAddress: SocketAddress? = null

        override fun write(frame: GatewayFrame) = Unit

        override fun close() = Unit
    }
}
