package com.mikai233.common.message

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.Json
import com.mikai233.protocol.idForClientMessage
import org.reflections.Reflections
import java.io.File
import kotlin.reflect.full.declaredMemberFunctions

class Cli {
    @Parameter(names = ["-p", "--package"], description = "handler package", required = true)
    lateinit var `package`: String

    @Parameter(names = ["-o", "--output"], description = "output path", required = true)
    lateinit var output: String

    @Parameter(names = ["-f", "--forward"], description = "forward target actor", required = true)
    lateinit var forward: Forward
}

fun main(args: Array<String>) {
    val cli = Cli()
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val protoIds = mutableSetOf<Int>()
    Reflections(cli.`package`).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
        clazz.kotlin.declaredMemberFunctions.forEach { kFunction ->
            MessageDispatcher.processFunction(GeneratedMessage::class, kFunction) {
                val id = idForClientMessage(it)
                check(!protoIds.contains(id)) { "proto id:$id already has a forward target" }
                protoIds.add(id)
            }
        }
    }
    File("${cli.output}/${cli.forward.name}.json").writeBytes(
        Json.toBytes(protoIds, true)
    )
}