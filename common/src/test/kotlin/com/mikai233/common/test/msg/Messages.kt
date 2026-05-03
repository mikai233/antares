@file:Suppress("unused")

package com.mikai233.common.test.msg

import com.mikai233.common.message.Message

object HandlerCtx

data class TestMessageA(val name: String) : Message

data class TestMessageB(val name: String, val age: Int) : Message

open class MessageHandlerA(
    private val events: MutableList<String>,
) {
    open fun handleTestMessageA(ctx: HandlerCtx, msg: TestMessageA) {
        events += "A:${msg.name}"
    }

    open fun handleTestMessageB(ctx: HandlerCtx, msg: TestMessageB) {
        events += "B:${msg.name}:${msg.age}"
    }
}
