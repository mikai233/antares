package com.mikai233.world.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.common.message.channel.UnsubscribeTopic
import com.mikai233.common.message.world.SubscribeTopicCrossWorld
import com.mikai233.common.message.world.UnsubscribeTopicCrossWorld
import com.mikai233.world.WorldActor
import com.mikai233.world.data.WorldActionMem

@AllOpen
class WorldService {
    fun onGameConfigUpdated(world: WorldActor) {
        val action = world.manager.get<WorldActionMem>().getOrCreateAction(WorldActionType.GameConfigVersion)
        val longHashcode = world.node.gameConfigManagerHashcode.asLong()
        if (longHashcode != action.actionParam) {
            action.actionParam = longHashcode
            world.fireEvent(GameConfigUpdatedEvent)
        }
    }

    fun subscribe(world: WorldActor, playerId: Long, playerWorldId: Long, topic: String) {
        if (world.worldId == playerWorldId) {
            world.sessionManager.sendRaw(playerId, SubscribeTopic(topic))
        } else {
            world.tellWorld(SubscribeTopicCrossWorld(playerWorldId, playerId, topic))
        }
    }

    fun unsubscribe(world: WorldActor, playerId: Long, playerWorldId: Long, topic: String) {
        if (world.worldId == playerId) {
            world.sessionManager.sendRaw(playerId, UnsubscribeTopic(topic))
        } else {
            world.tellWorld(UnsubscribeTopicCrossWorld(playerWorldId, playerId, topic))
        }
    }
}
