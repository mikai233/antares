package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.codec.*
import com.mikai233.common.crypto.AESCipher
import com.mikai233.protocol.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerCodecRoundTripTest {

    @Test
    fun testServerCodecRoundTripWithoutCrypto() {
        roundTripServerMessages()
    }

    @Test
    fun testServerCodecRoundTripWithCrypto() {
        roundTripServerMessages(AESCipher("1234567890123456"))
    }

    @Test
    fun testPacketCodecUsesIndependentSendAndRecvIndexes() {
        val channel = EmbeddedChannel(PacketCodec())
        try {
            repeat(2) { index ->
                val packet = Packet(100 + index, 4, Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4)))
                assertTrue(channel.writeOutbound(packet))
                channel.readOutbound<ByteBuf>().release()
            }

            val inbound = Unpooled.buffer(3 * Int.SIZE_BYTES + 4)
            inbound.writeInt(0)
            inbound.writeInt(999)
            inbound.writeInt(4)
            inbound.writeBytes(byteArrayOf(9, 8, 7, 6))

            assertTrue(channel.writeInbound(inbound))
            val packet = channel.readInbound<Packet>()
            try {
                assertEquals(999, packet.protoId)
                assertEquals(4, packet.originLen)
            } finally {
                packet.release()
            }
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun roundTripServerMessages(cipher: AESCipher? = null) {
        val outboundChannel =
            EmbeddedChannel(FrameCodec(1024 * 100), CryptoCodec(), PacketCodec(), LZ4Codec(), ProtobufCodec())
        val inboundChannel = EmbeddedChannel(FrameCodec(1024 * 100), CryptoCodec(), PacketCodec(), LZ4Codec())
        outboundChannel.attr(CIPHER_KEY).set(cipher)
        inboundChannel.attr(CIPHER_KEY).set(cipher)

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

        try {
            messages.forEach { message ->
                outboundChannel.writeOutbound(message)
                val encodedFrame = outboundChannel.readOutbound<ByteBuf>()
                try {
                    inboundChannel.writeInbound(encodedFrame.retainedDuplicate())
                    val packet = inboundChannel.readInbound<Packet>()
                    try {
                        val decodedMessage = decodeServerMessage(packet)
                        assertEquals(message, decodedMessage)
                    } finally {
                        packet.release()
                    }
                } finally {
                    encodedFrame.release()
                }
            }
        } finally {
            outboundChannel.finishAndReleaseAll()
            inboundChannel.finishAndReleaseAll()
        }
    }

    private fun decodeServerMessage(packet: Packet): GeneratedMessage {
        val parser = parserForServerMessage(packet.protoId)
        return ByteBufInputStream(packet.body.duplicate(), false).use(parser::parseFrom)
    }
}
