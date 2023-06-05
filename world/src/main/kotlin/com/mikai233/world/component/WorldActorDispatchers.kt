package com.mikai233.world.component

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.inject.XKoin
import com.mikai233.common.msg.MessageDispatcher
import com.mikai233.shared.message.BusinessWorldMessage
import org.koin.core.component.KoinComponent

class WorldActorDispatchers(private val koin: XKoin) : KoinComponent by koin {
    val protobufDispatcher = MessageDispatcher(GeneratedMessageV3::class, "com.mikai233.world.handler")
    val internalDispatcher = MessageDispatcher(BusinessWorldMessage::class, "com.mikai233.world.handler")
}
