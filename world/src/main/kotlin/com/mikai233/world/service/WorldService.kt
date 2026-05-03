package com.mikai233.world.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.core.gameConfigVersion
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.extension.tell
import com.mikai233.protocol.ProtoRpc.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.UnsubscribeTopicReq
import com.mikai233.world.WorldActor
import com.mikai233.world.data.WorldActionMem

@AllOpen
class WorldService {
    fun onGameConfigUpdated(world: WorldActor) {
        val action = world.manager.get<WorldActionMem>().getOrCreateAction(WorldActionType.GameConfigVersion)
        val longHashcode = world.node.gameConfigVersion.hashCode().toLong()
        if (longHashcode != action.actionParam) {
            action.actionParam = longHashcode
            world.self tell GameConfigUpdatedEvent
        }
    }

    fun subscribe(world: WorldActor, playerId: Long, playerWorldId: Long, topic: String) {
        if (world.worldId == playerWorldId) {
            world.sessionManager.send(
                playerId,
                SubscribeTopicReq.newBuilder()
                    .setTopic(topic)
                    .build(),
            )
        } else {
            world.tellWorld(
                CrossWorldSubscribeTopicReq.newBuilder()
                    .setWorldId(playerWorldId)
                    .setPlayerId(playerId)
                    .setTopic(topic)
                    .build(),
            )
        }
    }

    fun unsubscribe(world: WorldActor, playerId: Long, playerWorldId: Long, topic: String) {
        if (world.worldId == playerWorldId) {
            world.sessionManager.send(
                playerId,
                UnsubscribeTopicReq.newBuilder()
                    .setTopic(topic)
                    .build(),
            )
        } else {
            world.tellWorld(
                CrossWorldUnsubscribeTopicReq.newBuilder()
                    .setWorldId(playerWorldId)
                    .setPlayerId(playerId)
                    .setTopic(topic)
                    .build(),
            )
        }
    }
}
