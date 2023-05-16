package com.mikai233.shared.serde

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.ext.toByteArray
import com.mikai233.common.ext.toInt

internal fun protoMsgToPacket(message: GeneratedMessageV3, isClient: Boolean): ByteArray {
    val protoId = if (isClient) {
        GlobalProto.getClientMessageId(message::class)
    } else {
        GlobalProto.getServerMessageId(message::class)
    }
    val bodyBytes = message.toByteArray()
    return protoId.toByteArray() + bodyBytes
}

internal fun packetToProtoMsg(bytes: ByteArray, isClient: Boolean): GeneratedMessageV3 {
    val protoId = bytes.toInt()
    val parser = if (isClient) {
        GlobalProto.getClientMessageParser(protoId)
    } else {
        GlobalProto.getServerMessageParser(protoId)
    }
    return parser.parseFrom(bytes, Int.SIZE_BYTES, bytes.size - Int.SIZE_BYTES)
}

