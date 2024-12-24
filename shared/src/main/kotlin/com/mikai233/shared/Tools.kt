package com.mikai233.shared

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.protobufJsonPrinter
import com.mikai233.shared.message.ChannelProtobufEnvelope
import com.mikai233.shared.message.ClientMessage
import com.mikai233.shared.message.PlayerProtobufEnvelope
import com.mikai233.shared.message.WorldProtobufEnvelope
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
                "${message.message::class.simpleName} ${ProtobufPrinter.printer.print(message.message)}"
            }

            is ChannelProtobufEnvelope -> {
                "${message.message::class.simpleName} ${ProtobufPrinter.printer.print(message.message)}"
            }

            is PlayerProtobufEnvelope -> {
                "${message.message::class.simpleName} ${ProtobufPrinter.printer.print(message.message)}"
            }

            is WorldProtobufEnvelope -> {
                "${message.message::class.simpleName} ${ProtobufPrinter.printer.print(message.message)}"
            }


            else -> message.toString()
        }
        logger.info("{} {}", msgHeader(), formattedMessage)
    }
}
