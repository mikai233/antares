package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.shared.message.world.WakeupWorldReq
import com.mikai233.shared.message.world.WakeupWorldResp
import com.mikai233.world.WorldActor

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
@AllOpen
@Suppress("unused")
class WorldHandler : MessageHandler {
    @Handle(WakeupWorldReq::class)
    fun handleWakeupWorld(world: WorldActor) {
        world.sender tell WakeupWorldResp
    }
}