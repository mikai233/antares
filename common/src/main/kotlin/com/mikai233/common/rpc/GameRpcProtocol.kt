package com.mikai233.common.rpc

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.ProtoTest.TestReq
import io.github.realmlabs.asteria.cluster.pekko.PekkoMessageExtractor
import io.github.realmlabs.asteria.cluster.pekko.PekkoShardExtractors
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

    val playerShardExtractor by lazy {
        byEntityIdHash(PLAYER_SHARD_NUM) { message ->
            when (message) {
                is TestReq -> message.playerId.toString()
                is GmReq -> {
                    require(message.playerId != 0L) { "gm req missing player_id" }
                    message.playerId.toString()
                }
                else -> protocol.entityIds.requireEntityId(message)
            }
        }
    }

    val worldShardExtractor by lazy {
        byEntityIdHash(WORLD_SHARD_NUM) { message ->
            when (message) {
                is LoginReq -> message.worldId.toString()
                is GmReq -> {
                    require(message.worldId != 0L) { "gm req missing world_id" }
                    message.worldId.toString()
                }
                else -> protocol.entityIds.requireEntityId(message)
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
