package com.mikai233.common.rpc

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.runtime.PLAYER_SHARD_NUM
import com.mikai233.common.runtime.WORLD_SHARD_NUM
import com.mikai233.common.runtime.patchableServices
import io.github.realmlabs.asteria.cluster.pekko.PekkoMessageExtractor
import io.github.realmlabs.asteria.cluster.pekko.PekkoShardExtractors
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.rpc.protobuf.ProtobufRpcProtocol
import io.github.realmlabs.asteria.rpc.protobuf.ProtobufRpcProtocols

object GameRpcProtocol {
    /**
     * Generated internal protobuf RPC registry.
     *
     * Message ids come from `proto/protocol/rpc-protocol.json`, while shard
     * entity-id extraction for internal RPC messages comes from proto options.
     *
     * Client-facing protobuf messages such as [LoginReq] and [TestReq] are not
     * part of that internal registration model. Their shard routing is still
     * registered explicitly below because gateway/client routing is a separate concern.
     */
    val protocol: ProtobufRpcProtocol by lazy {
        ProtobufRpcProtocols.load(GameRpcProtocol::class.java.classLoader)
    }

    fun playerShardExtractor(node: NodeRuntime): PekkoMessageExtractor<GeneratedMessage> {
        return byEntityIdHash(PLAYER_SHARD_NUM) { message ->
            val entityId = node.patchableServices.require(RpcEntityIdResolver::class).resolvePlayer(message)
            requireNotNull(entityId) {
                "protobuf rpc entity id for ${message::class.qualifiedName} not found in player shard extractor"
            }
        }
    }

    fun worldShardExtractor(node: NodeRuntime): PekkoMessageExtractor<GeneratedMessage> {
        return byEntityIdHash(WORLD_SHARD_NUM) { message ->
            val entityId = node.patchableServices.require(RpcEntityIdResolver::class).resolveWorld(message)
            requireNotNull(entityId) {
                "protobuf rpc entity id for ${message::class.qualifiedName} not found in world shard extractor"
            }
        }
    }

    private fun byEntityIdHash(
        shardCount: Int,
        entityIdResolver: (GeneratedMessage) -> String,
    ): PekkoMessageExtractor<GeneratedMessage> {
        PekkoShardExtractors.validateShardCount(shardCount)
        return PekkoMessageExtractor(
            messageClass = GeneratedMessage::class.java,
            entityIdResolver = entityIdResolver,
            shardIdResolver = { _, entityId -> Math.floorMod(entityId.hashCode(), shardCount).toString() },
        )
    }
}
