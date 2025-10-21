package com.mikai233.common.serde

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.toByteArray
import com.mikai233.common.extension.toInt
import com.mikai233.protocol.idForClientMessage
import com.mikai233.protocol.idForServerMessage
import com.mikai233.protocol.parserForClientMessage
import com.mikai233.protocol.parserForServerMessage

internal fun protoMsgToPacket(message: GeneratedMessage, isClient: Boolean): ByteArray {
    val protoId = if (isClient) {
        idForClientMessage(message::class)
    } else {
        idForServerMessage(message::class)
    }
    val bodyBytes = message.toByteArray()
    return protoId.toByteArray() + bodyBytes
}

internal fun packetToProtoMsg(bytes: ByteArray, isClient: Boolean): GeneratedMessage {
    val protoId = bytes.toInt()
    val parser = if (isClient) {
        parserForClientMessage(protoId)
    } else {
        parserForServerMessage(protoId)
    }
    return parser.parseFrom(bytes, Int.SIZE_BYTES, bytes.size - Int.SIZE_BYTES)
}

