package com.mikai233.protocol

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import kotlin.reflect.KClass

public enum class SCEnum(
  public val id: Int,
) {
  PingResp(1),
  GmResp(2),
  TestResp(3),
  LoginResp(10001),
  TestNotify(99999),
  ;

  public companion object {
    private val entriesById: Map<Int, SCEnum> = entries.associateBy { it.id }

    public operator fun `get`(id: Int): SCEnum = requireNotNull(entriesById[id]) { "$id not found" }
  }
}

/**
 * Automatically generated field, do not modify
 */
private fun registerServerToClientMessageIdsChunk0(target: MutableMap<Class<out GeneratedMessage>, Int>) {
    target[ProtoSystem.PingResp::class.java] = 1
    target[ProtoSystem.GmResp::class.java] = 2
    target[ProtoTest.TestResp::class.java] = 3
    target[ProtoLogin.LoginResp::class.java] = 10_001
    target[ProtoLogin.TestNotify::class.java] = 99_999
}

/**
 * Automatically generated field, do not modify
 */
private fun registerServerToClientParsersByIdChunk0(target: MutableMap<Int, Parser<out GeneratedMessage>>) {
    target[1] = ProtoSystem.PingResp.parser()
    target[2] = ProtoSystem.GmResp.parser()
    target[3] = ProtoTest.TestResp.parser()
    target[10_001] = ProtoLogin.LoginResp.parser()
    target[99_999] = ProtoLogin.TestNotify.parser()
}

/**
 * Automatically generated field, do not modify
 */
private fun registerServerToClientParsersByTypeChunk0(target: MutableMap<Class<out GeneratedMessage>, Parser<out GeneratedMessage>>) {
    target[ProtoSystem.PingResp::class.java] = ProtoSystem.PingResp.parser()
    target[ProtoSystem.GmResp::class.java] = ProtoSystem.GmResp.parser()
    target[ProtoTest.TestResp::class.java] = ProtoTest.TestResp.parser()
    target[ProtoLogin.LoginResp::class.java] = ProtoLogin.LoginResp.parser()
    target[ProtoLogin.TestNotify::class.java] = ProtoLogin.TestNotify.parser()
}

/**
 * Automatically generated field, do not modify
 */
private val ServerToClientMessageIdByClass: Map<Class<out GeneratedMessage>, Int> =
    HashMap<Class<out GeneratedMessage>, Int>(5).apply {
        registerServerToClientMessageIdsChunk0(this)
    }

/**
 * Automatically generated field, do not modify
 */
private val ServerToClientParserById: Map<Int, Parser<out GeneratedMessage>> =
    HashMap<Int, Parser<out GeneratedMessage>>(5).apply {
        registerServerToClientParsersByIdChunk0(this)
    }

public fun idForServerMessage(messageClass: Class<out GeneratedMessage>): Int =
    requireNotNull(ServerToClientMessageIdByClass[messageClass]) {
        "Server proto id for ${messageClass.name} not found"
    }

public fun idForServerMessage(messageKClass: KClass<out GeneratedMessage>): Int = idForServerMessage(messageKClass.java)

public fun parserForServerMessage(id: Int): Parser<out GeneratedMessage> =
    requireNotNull(ServerToClientParserById[id]) {
        "parser for Server proto $id not found"
}

public fun registerServerParsersByType(target: MutableMap<Class<out GeneratedMessage>, Parser<out GeneratedMessage>>) {
    registerServerToClientParsersByTypeChunk0(target)
}
