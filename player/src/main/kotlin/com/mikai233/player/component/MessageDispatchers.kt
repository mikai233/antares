package com.mikai233.player.component

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.core.components.Component
import com.mikai233.common.msg.MessageDispatcher
import com.mikai233.shared.message.PlayerMessage

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
class MessageDispatchers : Component {
    val protobufDispatcher = MessageDispatcher(GeneratedMessageV3::class, "com.mikai233.player.handler")
    val internalDispatcher = MessageDispatcher(PlayerMessage::class, "com.mikai233.player.handler")

    override fun init() {
    }

    override fun shutdown() {
    }
}