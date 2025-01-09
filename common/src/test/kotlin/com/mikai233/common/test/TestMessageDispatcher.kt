package com.mikai233.common.test

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.Message
import com.mikai233.common.message.MessageDispatcher
import com.mikai233.common.message.MessageHandlerReflect
import com.mikai233.common.test.msg.HandlerCtx
import com.mikai233.common.test.msg.MessageHandlerA
import com.mikai233.common.test.msg.TestMessageA
import com.mikai233.common.test.msg.TestMessageB
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestMessageDispatcher {

    private val handlerReflect = MessageHandlerReflect("com.mikai233.common.test.msg")

    @AllOpen
    class MessageHandlerAFix : MessageHandlerA() {
        override fun handleTestMessageA(ctx: HandlerCtx, msg: TestMessageA) {
            throw Exception("fix logic")
        }
    }

    @Test
    fun testDispatchMessage() {
        val dispatcher = MessageDispatcher(Message::class, handlerReflect)
        with(dispatcher) {
            dispatch(TestMessageA::class, HandlerCtx, TestMessageA("hello"))
            dispatch(TestMessageB::class, HandlerCtx, TestMessageB("world", 18))
            assertThrows<IllegalArgumentException> {
                dispatch(TestMessageB::class, HandlerCtx, TestMessageA("hello"))
            }
        }
    }

    @Test
    fun testReplaceHandler() {
        val dispatcher = MessageDispatcher(Message::class, handlerReflect)
        with(dispatcher) {
            dispatch(TestMessageA::class, HandlerCtx, TestMessageA("hello"))
            handlerReflect.replace<MessageHandlerA>(MessageHandlerAFix())
            assertThrows<Exception> {
                dispatch(TestMessageA::class, HandlerCtx, TestMessageA("hello"))
            }
        }
    }
}
