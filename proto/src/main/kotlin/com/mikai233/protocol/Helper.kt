package com.mikai233.protocol

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import kotlin.reflect.KClass

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/7
 */
fun idForClientMessage(messageKClass: KClass<out GeneratedMessage>): Int {
    return requireNotNull(ClientToServerMessageById[messageKClass]) {
        "client proto id for ${messageKClass.qualifiedName} not found"
    }
}

fun parserForClientMessage(id: Int): Parser<out GeneratedMessage> {
    return requireNotNull(ClientToServerParserById[id]) {
        "parser for client proto $id not found"
    }
}

fun idForServerMessage(messageKClass: KClass<out GeneratedMessage>): Int {
    return requireNotNull(ServerToClientMessageById[messageKClass]) {
        "server proto id for ${messageKClass.qualifiedName} not found"
    }
}

fun parserForServerMessage(id: Int): Parser<out GeneratedMessage> {
    return requireNotNull(ServerToClientParserById[id]) {
        "parser for server proto $id not found"
    }
}
