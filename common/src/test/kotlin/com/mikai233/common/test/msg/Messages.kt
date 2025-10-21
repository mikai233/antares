@file:Suppress("unused")

package com.mikai233.common.test.msg

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.Handle
import com.mikai233.common.extension.logger
import com.mikai233.common.message.Message
import com.mikai233.common.message.MessageHandler

object HandlerCtx

data class TestMessageA(val name: String) : Message

data class TestMessageB(val name: String, val age: Int) : Message

@AllOpen
class MessageHandlerA : MessageHandler {
    private val logger = logger()

    @Handle
    fun handleTestMessageA(ctx: HandlerCtx, msg: TestMessageA) {
        logger.info("handle msg:{}", msg)
    }

    @Handle
    fun handleTestMessageB(ctx: HandlerCtx, msg: TestMessageB) {
        logger.info("handle msg:{}", msg)
    }
}

@AllOpen
class MessageHandlerB : MessageHandler
