package com.mikai233.common.test

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.Message
import com.mikai233.common.message.MessageDispatcher
import com.mikai233.common.test.msg.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestMessageDispatcher {

    @AllOpen
    class MessageHandlerAFix : MessageHandlerA() {
        override fun handleTestMessageA(ctx: HandlerCtx, msg: TestMessageA) {
            throw Exception("fix logic")
        }
    }

    @Test
    fun testDispatchMessage() {
        val dispatcher = MessageDispatcher(Message::class, "com.mikai233.common.test.msg")
        with(dispatcher) {
            dispatch(TestMessageA::class, HandlerCtx, TestMessageA("hello"))
            dispatch(TestMessageB::class, HandlerCtx, TestMessageB("world", 18))
            assertThrows<IllegalArgumentException> {
                dispatch(TestMessageB::class, HandlerCtx, TestMessageA("hello"))
            }
        }
    }

    @Test
    fun testUpdateHandler() {
        val dispatcher = MessageDispatcher(Message::class, "com.mikai233.common.test.msg")
        with(dispatcher) {
            dispatch(TestMessageA::class, HandlerCtx, TestMessageA("hello"))
            updateHandler(MessageHandlerA::class, MessageHandlerAFix())
            assertThrows<Exception> {
                dispatch(TestMessageA::class, HandlerCtx, TestMessageA("hello"))
            }
            assertThrows<IllegalStateException> {
                updateHandler(MessageHandlerB::class, MessageHandlerAFix())
            }
        }
    }
}
