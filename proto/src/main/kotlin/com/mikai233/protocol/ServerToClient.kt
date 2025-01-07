package com.mikai233.protocol

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import kotlin.reflect.KClass

/**
 * Automatically generated field, do not modify
 */
private val ServerToClientMessageById0: Map<KClass<out GeneratedMessage>, Int> = mapOf(
    ProtoSystem.PingResp::class to 1,
    ProtoSystem.GmResp::class to 2,
    ProtoTest.TestResp::class to 3,
    ProtoLogin.LoginResp::class to 10_001,
    ProtoLogin.TestNotify::class to 99_999
)

/**
 * Automatically generated field, do not modify
 */
public val ServerToClientMessageById: Map<KClass<out GeneratedMessage>, Int> = listOf(
    ServerToClientMessageById0,
).flatMap { it.entries.map { entry -> entry.key to entry.value } }.toMap()

/**
 * Automatically generated field, do not modify
 */
private val ServerToClientParserById0: Map<Int, Parser<out GeneratedMessage>> = mapOf(
    1 to ProtoSystem.PingResp.parser(),
    2 to ProtoSystem.GmResp.parser(),
    3 to ProtoTest.TestResp.parser(),
    10_001 to ProtoLogin.LoginResp.parser(),
    99_999 to ProtoLogin.TestNotify.parser()
)

/**
 * Automatically generated field, do not modify
 */
public val ServerToClientParserById: Map<Int, Parser<out GeneratedMessage>> = listOf(
    ServerToClientParserById0,
).flatMap { it.entries.map { entry -> entry.key to entry.value } }.toMap()

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
