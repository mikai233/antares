@file:Suppress("MatchingDeclarationName")

package com.mikai233.common.message

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.annotation.Gm
import com.mikai233.common.extension.Json
import com.mikai233.protocol.idForClientMessage
import org.reflections.Reflections
import java.io.File
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation

private class Cli {
    @Parameter(names = ["-p", "--package"], description = "handler package", required = true)
    @Suppress("VariableNaming")
    lateinit var `package`: String

    @Parameter(names = ["-o", "--output"], description = "output path", required = true)
    lateinit var output: String

    @Parameter(names = ["-f", "--forward"], description = "forward target actor", required = true)
    lateinit var forward: Forward
}

data class ForwardMap(
    val protos: List<Int>,
    val commands: List<String>,
)

fun main(args: Array<String>) {
    val cli = Cli()
    @Suppress("SpreadOperator")
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val protoIds = mutableSetOf<Int>()
    val commands = mutableSetOf<String>()
    val receiverCount = if (cli.forward == Forward.WorldActor) 2 else 1
    Reflections(cli.`package`).getSubTypesOf(MessageHandler::class.java).forEach { clazz ->
        clazz.kotlin.declaredMemberFunctions.forEach { kFunction ->
            MessageDispatcher.processFunction(GeneratedMessage::class, kFunction, receiverCount) {
                val id = idForClientMessage(it)
                check(!protoIds.contains(id)) { "proto id:$id already has a forward target" }
                protoIds.add(id)
            }
            val gmAnnotation = kFunction.findAnnotation<Gm>()
            if (gmAnnotation != null) {
                commands.add(gmAnnotation.command)
            }
        }
    }
    val forwardMap = ForwardMap(protoIds.toList(), commands.toList())
    File("${cli.output}/${cli.forward.name}.json").writeBytes(Json.toBytes(forwardMap, true))
}
