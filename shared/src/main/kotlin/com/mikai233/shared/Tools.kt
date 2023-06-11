package com.mikai233.shared

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.ext.invokeOnTargetMode
import com.mikai233.common.ext.protobufJsonPrinter
import com.mikai233.shared.message.ChannelProtobufEnvelope
import com.mikai233.shared.message.ClientMessage
import org.slf4j.Logger

object ProtobufPrinter {
    val printer = protobufJsonPrinter()
}

fun logMessage(logger: Logger, message: Any, msgHeader: () -> String = { "" }) {
    invokeOnTargetMode(setOf(ServerMode.DevMode)) {
        val formattedMessage = when (message) {
            is GeneratedMessageV3 -> {
                "${message::class.simpleName} ${ProtobufPrinter.printer.print(message)}"
            }

            is ClientMessage -> {
                "${message.inner::class.simpleName} ${ProtobufPrinter.printer.print(message.inner)}"
            }

            is ChannelProtobufEnvelope -> {
                "${message.inner::class.simpleName} ${ProtobufPrinter.printer.print(message.inner)}"
            }


            else -> message.toString()
        }
        logger.info("{} {}", msgHeader(), formattedMessage)
    }
}
