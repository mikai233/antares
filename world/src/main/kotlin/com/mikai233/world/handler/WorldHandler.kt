package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.Gm
import com.mikai233.common.annotation.Handle
import com.mikai233.common.broadcast.Topic
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.MessageHandler
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.common.message.channel.UnsubscribeTopic
import com.mikai233.common.message.world.SubscribeTopicCrossWorld
import com.mikai233.common.message.world.UnsubscribeTopicCrossWorld
import com.mikai233.common.message.world.WakeupWorldReq
import com.mikai233.common.message.world.WakeupWorldResp
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.testNotify
import com.mikai233.world.PlayerSession
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

    @Handle
    fun handleGmReq(world: WorldActor, session: PlayerSession, req: GmReq) {
        invokeOnTargetMode(ServerMode.DevMode) {
            world.node.gmDispatcher.dispatch(
                req.cmd,
                req.paramsList,
                world,
                session,
            )
        }
    }

    @Handle
    fun handleSubscribeTopicCrossWorld(world: WorldActor, subscribe: SubscribeTopicCrossWorld) {
        world.sessionManager.sendRaw(subscribe.playerId, SubscribeTopic(subscribe.topic))
    }

    fun handleUnsubscribeTopicCrossWorld(world: WorldActor, unsubscribe: UnsubscribeTopicCrossWorld) {
        world.sessionManager.sendRaw(unsubscribe.playerId, UnsubscribeTopic(unsubscribe.topic))
    }

    @Gm("testBroadcast")
    fun testBroadcast(world: WorldActor, session: PlayerSession) {
        world.broadcast(testNotify { }, Topic.ofWorld(world.worldId), emptySet(), emptySet())
    }
}
