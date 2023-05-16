@file:Suppress("unused")

package com.mikai233.common.test.msg

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.ext.logger
import com.mikai233.common.msg.Message
import com.mikai233.common.msg.MessageHandler

object HandlerCtx

data class TestMessageA(val name: String) : Message

data class TestMessageB(val name: String, val age: Int) : Message

@AllOpen
class MessageHandlerA : MessageHandler {
    private val logger = logger()
    fun handleTestMessageA(ctx: HandlerCtx, msg: TestMessageA) {
        logger.info("handle msg:{}", msg)
    }

    fun handleTestMessageB(ctx: HandlerCtx, msg: TestMessageB) {
        logger.info("handle msg:{}", msg)
    }
}

@AllOpen
class MessageHandlerB : MessageHandler {

}