package com.mikai233.common.rpc

import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.core.GameEntityKinds
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoRpc.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.PlayerChannelClosedReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateResp
import com.mikai233.protocol.ProtoRpc.PlayerLoginReq
import com.mikai233.protocol.ProtoRpc.PlayerLoginResp
import com.mikai233.protocol.ProtoRpc.WorldWakeupReq
import com.mikai233.protocol.ProtoRpc.WorldWakeupResp
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.ProtoTest.TestReq
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.rpc.RpcProtocol
import io.github.realmlabs.asteria.rpc.protobuf.GeneratedProtobufRpcProtocol
import io.github.realmlabs.asteria.rpc.protobuf.ProtobufRpcProtocolBuilder
import io.github.realmlabs.asteria.rpc.RpcTarget

object GameRpcProtocolDefinition : GeneratedProtobufRpcProtocol() {
    val protocol: RpcProtocol by lazy { create() }

    val playerShardExtractor by lazy {
        io.github.realmlabs.asteria.cluster.pekko.PekkoRpcShardExtractors.byRpcEntityId(
            PLAYER_SHARD_NUM,
            protocol.entityIds,
        )
    }

    val worldShardExtractor by lazy {
        io.github.realmlabs.asteria.cluster.pekko.PekkoRpcShardExtractors.byRpcEntityId(
            WORLD_SHARD_NUM,
            protocol.entityIds,
        )
    }

    override fun contribute(builder: ProtobufRpcProtocolBuilder) {
        val playerTarget = RpcTarget.Entity(EntityKind(GameEntityKinds.PlayerActor))
        val worldTarget = RpcTarget.Entity(EntityKind(GameEntityKinds.WorldActor))

        builder.tell(
            id = 10_001,
            name = "world.login",
            target = worldTarget,
            requestParser = LoginReq.parser(),
            entityIdResolver = { it.worldId.toString() },
        )
        builder.tell(
            id = 10_002,
            name = "player.test",
            target = playerTarget,
            requestParser = TestReq.parser(),
            entityIdResolver = { it.playerId.toString() },
        )
        builder.call(
            id = 10_003,
            name = "player.create",
            target = playerTarget,
            requestParser = PlayerCreateReq.parser(),
            responseId = 10_004,
            responseParser = PlayerCreateResp.parser(),
            entityIdResolver = { it.playerId.toString() },
        )
        builder.call(
            id = 10_005,
            name = "player.login",
            target = playerTarget,
            requestParser = PlayerLoginReq.parser(),
            responseId = 10_006,
            responseParser = PlayerLoginResp.parser(),
            entityIdResolver = { it.playerId.toString() },
        )
        builder.tell(
            id = 10_011,
            name = "player.channel_closed",
            target = playerTarget,
            requestParser = PlayerChannelClosedReq.parser(),
            entityIdResolver = { it.playerId.toString() },
        )
        builder.call(
            id = 10_012,
            name = "world.wakeup",
            target = worldTarget,
            requestParser = WorldWakeupReq.parser(),
            responseId = 10_013,
            responseParser = WorldWakeupResp.parser(),
            entityIdResolver = { it.worldId.toString() },
        )
        builder.tell(
            id = 10_014,
            name = "world.subscribe_topic",
            target = worldTarget,
            requestParser = CrossWorldSubscribeTopicReq.parser(),
            entityIdResolver = { it.worldId.toString() },
        )
        builder.tell(
            id = 10_015,
            name = "world.unsubscribe_topic",
            target = worldTarget,
            requestParser = CrossWorldUnsubscribeTopicReq.parser(),
            entityIdResolver = { it.worldId.toString() },
        )
        builder.entityId(GmReq::class.java) {
            when {
                it.playerId != 0L -> it.playerId.toString()
                it.worldId != 0L -> it.worldId.toString()
                else -> error("gm req missing player_id/world_id")
            }
        }
    }
}
