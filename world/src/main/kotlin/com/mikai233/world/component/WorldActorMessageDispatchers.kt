package com.mikai233.world.component

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.component.Component
import com.mikai233.common.msg.MessageDispatcher

class WorldActorMessageDispatchers : Component {
    val protobufDispatcher = MessageDispatcher(GeneratedMessageV3::class, "com.mikai233.world.handler")
//    val internalDispatcher = MessageDispatcher(BusinessPlayerMessage::class, "com.mikai233.player.handler")
}