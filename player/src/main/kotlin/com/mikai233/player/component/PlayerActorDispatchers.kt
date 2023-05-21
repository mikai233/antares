package com.mikai233.player.component

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.components.Component
import com.mikai233.common.msg.MessageDispatcher
import com.mikai233.shared.message.BusinessPlayerMessage

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
class PlayerActorDispatchers : Component {
    val protobufDispatcher = MessageDispatcher(GeneratedMessageV3::class, "com.mikai233.player.handler")
    val internalDispatcher = MessageDispatcher(BusinessPlayerMessage::class, "com.mikai233.player.handler")
}