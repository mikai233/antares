package com.mikai233.protocol

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import kotlin.reflect.KClass

/**
 * Automatically generated field, do not modify
 */
private val ClientToServerMessageById0: Map<KClass<out GeneratedMessage>, Int> = mapOf(
    ProtoSystem.PingReq::class to 1,
    ProtoSystem.GmReq::class to 2,
    ProtoTest.TestReq::class to 3,
    ProtoLogin.LoginReq::class to 10_001
)

/**
 * Automatically generated field, do not modify
 */
public val ClientToServerMessageById: Map<KClass<out GeneratedMessage>, Int> = listOf(
    ClientToServerMessageById0,
).flatMap { it.entries.map { entry -> entry.key to entry.value } }.toMap()

/**
 * Automatically generated field, do not modify
 */
private val ClientToServerParserById0: Map<Int, Parser<out GeneratedMessage>> = mapOf(
    1 to ProtoSystem.PingReq.parser(),
    2 to ProtoSystem.GmReq.parser(),
    3 to ProtoTest.TestReq.parser(),
    10_001 to ProtoLogin.LoginReq.parser()
)

/**
 * Automatically generated field, do not modify
 */
public val ClientToServerParserById: Map<Int, Parser<out GeneratedMessage>> = listOf(
    ClientToServerParserById0,
).flatMap { it.entries.map { entry -> entry.key to entry.value } }.toMap()

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
