package com.mikai233.shared

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.protobufJsonPrinter
import com.mikai233.shared.message.ClientProtobuf
import com.mikai233.shared.message.ProtobufEnvelope
import com.mikai233.shared.message.ServerProtobuf

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

        is ProtobufEnvelope -> {
            "${message.message::class.simpleName} ${ProtobufPrinter.print(message.message)}"
        }

        else -> message.toString()
    }
}