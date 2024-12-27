package com.mikai233.shared

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.protobufJsonPrinter
import com.mikai233.shared.message.ChannelProtobufEnvelope
import com.mikai233.shared.message.ClientMessage
import com.mikai233.shared.message.ProtobufEnvelope
import com.mikai233.shared.message.world.WorldProtobufEnvelope
import org.slf4j.Logger

object ProtobufPrinter {
    val printer = protobufJsonPrinter()
}

fun logMessage(logger: Logger, message: Any, msgHeader: () -> String = { "" }) {
    invokeOnTargetMode(setOf(ServerMode.DevMode)) {
        val formattedMessage = when (message) {
            is GeneratedMessage -> {
                "${message::class.simpleName} ${ProtobufPrinter.printer.print(message)}"
            }

            is ClientMessage -> {
                "${message.message::class.simpleName} ${ProtobufPrinter.printer.print(message.message)}"
            }

            is ChannelProtobufEnvelope -> {
                "${message.message::class.simpleName} ${ProtobufPrinter.printer.print(message.message)}"
            }

            is ProtobufEnvelope -> {
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
