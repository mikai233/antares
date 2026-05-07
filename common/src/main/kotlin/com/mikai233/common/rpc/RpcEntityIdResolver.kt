package com.mikai233.common.rpc

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.runtime.patchableServices
import com.mikai233.common.runtime.replacePatchableService
import com.mikai233.protocol.ProtoChat.ChatHistoryReq
import com.mikai233.protocol.ProtoChat.ChatSendReq
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.ProtoTest.TestReq
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.rpc.protobuf.MissingProtobufRpcEntityIdException
import io.github.realmlabs.asteria.rpc.protobuf.ProtobufRpcProtocol

interface RpcEntityIdResolver {
    fun resolvePlayer(message: GeneratedMessage): String?

    fun resolveWorld(message: GeneratedMessage): String?
}

class DefaultRpcEntityIdResolver(
    private val protocol: ProtobufRpcProtocol,
) : RpcEntityIdResolver {
    override fun resolvePlayer(message: GeneratedMessage): String? {
        return when (message) {
            is TestReq -> message.playerId.toString()
            is ChatSendReq -> message.playerId.toString()
            is ChatHistoryReq -> message.playerId.toString()
            is GmReq -> message.playerId.takeIf { it != 0L }?.toString()
            else -> protocol.entityIds.findEntityIdOrNull(message)
        }
    }

    override fun resolveWorld(message: GeneratedMessage): String? {
        return when (message) {
            is LoginReq -> message.worldId.toString()
            is GmReq -> message.worldId.takeIf { it != 0L }?.toString()
            else -> protocol.entityIds.findEntityIdOrNull(message)
        }
    }
}

class FieldOverrideRpcEntityIdResolver(
    private val delegate: RpcEntityIdResolver,
    private val playerFieldOverrides: Map<String, String> = emptyMap(),
    private val worldFieldOverrides: Map<String, String> = emptyMap(),
) : RpcEntityIdResolver {
    override fun resolvePlayer(message: GeneratedMessage): String? {
        return resolveOverride(message, playerFieldOverrides) ?: delegate.resolvePlayer(message)
    }

    override fun resolveWorld(message: GeneratedMessage): String? {
        return resolveOverride(message, worldFieldOverrides) ?: delegate.resolveWorld(message)
    }

    private fun resolveOverride(message: GeneratedMessage, overrides: Map<String, String>): String? {
        val fieldName = overrides[message.javaClass.name] ?: return null
        val field = message.descriptorForType.findFieldByName(fieldName)
            ?: error("protobuf field '$fieldName' not found on ${message.javaClass.name}")
        val value = message.getField(field)
        return when (value) {
            is Number -> value.toLong().toString()
            is String -> value.takeIf { it.isNotBlank() }
            else -> error("protobuf field '$fieldName' on ${message.javaClass.name} must resolve to string/number")
        }
    }
}

fun NodeRuntime.installRpcEntityIdFieldOverrides(
    playerFieldOverrides: Map<String, String> = emptyMap(),
    worldFieldOverrides: Map<String, String> = emptyMap(),
) {
    val current = patchableServices.require(RpcEntityIdResolver::class)
    replacePatchableService(
        RpcEntityIdResolver::class,
        FieldOverrideRpcEntityIdResolver(current, playerFieldOverrides, worldFieldOverrides),
        patchId = "script:rpc-entity-id-resolver-hotfix",
    )
}

private fun io.github.realmlabs.asteria.rpc.protobuf.ProtobufRpcEntityIdRegistry.findEntityIdOrNull(
    message: GeneratedMessage,
): String? {
    return try {
        requireEntityId(message)
    } catch (_: MissingProtobufRpcEntityIdException) {
        null
    }
}
