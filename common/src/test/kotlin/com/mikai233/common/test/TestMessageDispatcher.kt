package com.mikai233.common.test

import com.mikai233.common.message.ActorMessageDispatcher
import com.mikai233.common.message.Message
import com.mikai233.common.test.msg.HandlerCtx
import com.mikai233.common.test.msg.MessageHandlerA
import com.mikai233.common.test.msg.TestMessageA
import com.mikai233.common.test.msg.TestMessageB
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestMessageDispatcher {
    @Test
    fun testDispatchMessage() {
        val events = mutableListOf<String>()
        val handler = MessageHandlerA(events)
        val dispatcher = ActorMessageDispatcher<HandlerCtx, Message>(TestRuntime).apply {
            register(TestMessageA::class, handler::handleTestMessageA)
            register(TestMessageB::class, handler::handleTestMessageB)
        }

        dispatcher.dispatch(HandlerCtx, TestMessageA("hello"))
        dispatcher.dispatch(HandlerCtx, TestMessageB("world", 18))

        assertEquals(listOf("A:hello", "B:world:18"), events)
        assertThrows<ClassCastException> {
            dispatcher.dispatch(HandlerCtx, TestMessageB::class, TestMessageA("hello"))
        }
    }

    private object TestRuntime : NodeRuntime {
        override val name: String = "test"
        override val roles: Set<RoleKey> = emptySet()
        override val state: NodeState = NodeState.Started
        override val services: ServiceRegistry = ServiceRegistry()
    }
}
