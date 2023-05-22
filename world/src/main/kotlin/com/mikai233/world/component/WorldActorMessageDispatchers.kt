package com.mikai233.world.component

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.component.Component
import com.mikai233.common.msg.MessageDispatcher
import com.mikai233.shared.message.BusinessWorldMessage

class WorldActorMessageDispatchers : Component {
    val protobufDispatcher = MessageDispatcher(GeneratedMessageV3::class, "com.mikai233.world.handler")
    val internalDispatcher = MessageDispatcher(BusinessWorldMessage::class, "com.mikai233.world.handler")
}