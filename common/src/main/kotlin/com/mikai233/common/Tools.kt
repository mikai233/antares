package com.mikai233.common

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.protobufJsonPrinter
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.common.message.PlayerProtobufEnvelope
import com.mikai233.common.message.ServerProtobuf
import com.mikai233.common.message.WorldProtobufEnvelope

private val ProtobufPrinter = protobufJsonPrinter()

fun formatMessage(message: Any): String {
    return when (message) {
        is GeneratedMessage -> {
            "${message::class.simpleName} ${ProtobufPrinter.print(message)}"
        }

        is ClientProtobuf -> {
            "${message.message::class.simpleName} ${ProtobufPrinter.print(message.message)}"
        }

        is ServerProtobuf -> {
            "${message.message::class.simpleName} ${ProtobufPrinter.print(message.message)}"
        }

        is PlayerProtobufEnvelope -> {
            "${message.message::class.simpleName} ${ProtobufPrinter.print(message.message)}"
        }

        is WorldProtobufEnvelope -> {
            "${message.message::class.simpleName} ${ProtobufPrinter.print(message.message)}"
        }

        else -> message.toString()
    }
}
