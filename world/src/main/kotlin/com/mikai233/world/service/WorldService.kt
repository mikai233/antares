package com.mikai233.world.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.event.GameConfigUpdatedEvent
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
}