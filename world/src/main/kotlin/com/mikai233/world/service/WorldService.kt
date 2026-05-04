package com.mikai233.world.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.SubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.UnsubscribeTopicReq
import com.mikai233.world.WorldActor

@AllOpen
class WorldService {
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
