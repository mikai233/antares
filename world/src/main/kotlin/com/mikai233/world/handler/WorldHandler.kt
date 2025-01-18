package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.common.message.world.WakeupWorldReq
import com.mikai233.common.message.world.WakeupWorldResp
import com.mikai233.world.WorldActor
import com.mikai233.world.service.worldService

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
@AllOpen
@Suppress("unused")
class WorldHandler : MessageHandler {
    val logger = logger()

    @Handle(WakeupWorldReq::class)
    fun handleWakeupWorld(world: WorldActor) {
        world.sender tell WakeupWorldResp
    }

    @Handle(WorldActiveEvent::class)
    fun handleWorldActiveEvent(world: WorldActor) {
        tryCatch(logger) { worldService.onGameConfigUpdated(world) }
    }
}
