package com.mikai233.protocol

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import kotlin.reflect.KClass

public enum class CSEnum(
    public val id: Int,
) {
    PingReq(1),
    GmReq(2),
    TestReq(3),
    LoginReq(10001),
    ;

    public companion object {
        private val entriesById: Map<Int, CSEnum> = entries.associateBy { it.id }

        public operator fun `get`(id: Int): CSEnum = requireNotNull(entriesById[id]) { "$id not found" }
    }
}

/**
 * Automatically generated field, do not modify
 */
private fun registerClientToServerMessageIdsChunk0(target: MutableMap<Class<out GeneratedMessage>, Int>) {
    target[ProtoSystem.PingReq::class.java] = 1
    target[ProtoSystem.GmReq::class.java] = 2
    target[ProtoTest.TestReq::class.java] = 3
    target[ProtoLogin.LoginReq::class.java] = 10_001
}

/**
 * Automatically generated field, do not modify
 */
private fun registerClientToServerParsersByIdChunk0(target: MutableMap<Int, Parser<out GeneratedMessage>>) {
    target[1] = ProtoSystem.PingReq.parser()
    target[2] = ProtoSystem.GmReq.parser()
    target[3] = ProtoTest.TestReq.parser()
    target[10_001] = ProtoLogin.LoginReq.parser()
}

/**
 * Automatically generated field, do not modify
 */
private fun registerClientToServerParsersByTypeChunk0(target: MutableMap<Class<out GeneratedMessage>, Parser<out GeneratedMessage>>) {
    target[ProtoSystem.PingReq::class.java] = ProtoSystem.PingReq.parser()
    target[ProtoSystem.GmReq::class.java] = ProtoSystem.GmReq.parser()
    target[ProtoTest.TestReq::class.java] = ProtoTest.TestReq.parser()
    target[ProtoLogin.LoginReq::class.java] = ProtoLogin.LoginReq.parser()
}

/**
 * Automatically generated field, do not modify
 */
private val ClientToServerMessageIdByClass: Map<Class<out GeneratedMessage>, Int> =
    HashMap<Class<out GeneratedMessage>, Int>(4).apply {
        registerClientToServerMessageIdsChunk0(this)
    }

/**
 * Automatically generated field, do not modify
 */
private val ClientToServerParserById: Map<Int, Parser<out GeneratedMessage>> =
    HashMap<Int, Parser<out GeneratedMessage>>(4).apply {
        registerClientToServerParsersByIdChunk0(this)
    }

public fun idForClientMessage(messageClass: Class<out GeneratedMessage>): Int =
    requireNotNull(ClientToServerMessageIdByClass[messageClass]) {
        "Client proto id for ${messageClass.name} not found"
    }

public fun idForClientMessage(messageKClass: KClass<out GeneratedMessage>): Int = idForClientMessage(messageKClass.java)

public fun parserForClientMessage(id: Int): Parser<out GeneratedMessage> =
    requireNotNull(ClientToServerParserById[id]) {
        "parser for Client proto $id not found"
    }

public fun registerClientParsersByType(target: MutableMap<Class<out GeneratedMessage>, Parser<out GeneratedMessage>>) {
    registerClientToServerParsersByTypeChunk0(target)
}
