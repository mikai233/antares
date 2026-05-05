package com.mikai233.common.message

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.protobufJsonPrinter

private val ProtobufPrinter = protobufJsonPrinter()

fun formatMessage(message: Any): String {
    return when (message) {
        is GeneratedMessage -> {
            "${message::class.simpleName} ${ProtobufPrinter.print(message)}"
        }

        is ClientProtobuf -> {
            "${message.message::class.simpleName} ${ProtobufPrinter.print(message.message)}"
        }

        else -> message.toString()
    }
}
