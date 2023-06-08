package com.mikai233.global.component

import com.mikai233.common.inject.XKoin
import com.mikai233.common.msg.MessageDispatcher
import com.mikai233.shared.message.BusinessPlayerMessage

class GlobalActorDispatcher(private val koin: XKoin) {
    val internalDispatcher = MessageDispatcher(BusinessPlayerMessage::class, "com.mikai233.global.handler")
}
